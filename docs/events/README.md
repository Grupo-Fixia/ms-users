# Contrato de eventos de negocio de ms-users (GC-443)

Versión 1, Sprint 5. `ms-users` publica `evaluacion.registrada` (GC-444) y, desde GC-524, `empresa.tecnico_vinculado` y
`empresa.tecnico_desvinculado`. Sigue las
mismas reglas que el contrato de eventos de `ms-services` (GC-436, `ms-services/docs/events/README.md`): eventos
completos sin datos personales, Outbox en la misma transacción, entrega al menos una vez con `eventId` estable y
versionado por tipo. Aquí solo se anota lo que cambia. Las decisiones están en
[`../contracts/evaluaciones.md`](../contracts/evaluaciones.md).

Los esquemas están en [`schemas/`](schemas/) (JSON Schema 2020-12, con sus propios `$id` bajo
`https://fixia.local/schemas/events/ms-users/`) y cuatro ejemplos en [`examples/`](examples/). Si este texto y un esquema
no coinciden, manda el esquema.

## Lo que cambia frente al contrato de ms-services

| Campo | ms-services | ms-users |
| --- | --- | --- |
| Tópico | `fixia.services.service-requests` | `fixia.users.evaluations` |
| Clave del mensaje | `aggregate.id` (la solicitud) | `data.evaluatedId`: todas las evaluaciones de una persona en la misma partición, en orden |
| `producer` | `ms-services` | `ms-users` |
| `aggregate` | `ServiceRequest`, secuencia por solicitud | `Evaluation`, `id` = `evaluationId`, `sequence` siempre 1 (hecho único e inmutable) |
| `correlationId` | Id de la solicitud | El mismo: id de la solicitud de `ms-services`, que enlaza la evaluación con su servicio |
| `actor.role` | `CLIENT`, `PROFESSIONAL`, `COMPANY`, `SYSTEM` | `CLIENT` o `PROFESSIONAL` (las empresas no evalúan, RN-017) |
| Outbox | `outbox_events` de `ms-services` | `outbox_events` de `ms-users`, **mismas columnas**, para que el relay de GC-434 las lea igual |

Cabeceras, formato de fechas, idempotencia por `eventId` y versionado: iguales que en `ms-services`.

## Catálogo

| Evento | Cuándo | Actor | Datos |
| --- | --- | --- | --- |
| `evaluacion.registrada` | Al crear una evaluación (`POST /api/users/evaluations`, 201). Repetir la misma (200) no publica otro | Quien evalúa | `evaluationId`, `serviceRequestId`, `direction`, `evaluatorId`, `evaluatedId`, `rating` (1–5), `source`, `serviceClosedAt` |
| `empresa.tecnico_vinculado` | El técnico acepta una invitación (`POST /api/users/technicians/me/invitations/{id}/accept`, GC-524) | El técnico | `companyId`, `companyAdminUserId`, `technicianUserId`, `invitationId`, `joinedAt` |
| `empresa.tecnico_desvinculado` | El técnico se desvincula (`DELETE /api/users/technicians/me/company`, GC-524) | El técnico | `companyId`, `companyAdminUserId`, `technicianUserId`, `leftAt` |
| `sesion.revocada` | Un cierre de sesión revoca un access token que no estaba revocado (`POST /api/users/auth/logout`, GC-547). Repetir el cierre no publica otro | El dueño de la sesión (cualquier rol) | `jti`, `expiresAt`, `revokedAt` |

Los eventos de empresa van al tópico `fixia.users.companies` con clave = `technicianUserId`. Su agregado es la
asociación (`CompanyMembership`, `id` = `company_technicians.id`): la vinculación es la secuencia 1 y la desvinculación la
2, y `correlationId` es esa misma asociación. Sin correo, nombre ni documento. `companyAdminUserId` es el `providerId`
con que ms-services y ms-payments conocen a la empresa.

**No viaja el comentario** de la evaluación: puede traer datos personales. Lo consultan solo el evaluador y el evaluado
por la API.

`sesion.revocada` va al tópico `fixia.users.sessions` con clave = `jti`; su agregado es la sesión (`Session`, `id` =
`jti`, secuencia 1). Por eso el sobre admite ahora los roles `COMPANY` y `ADMIN` en `actor.role` (cambio compatible).
No lleva el token ni datos personales.

**Cómo se usa (GC-547, cero dependencias síncronas; el PO aceptó una ventana corta):** ms-services, ms-payments y
ms-matching-geo ya no consultan `/internal/tokens/{jti}/status`. Cada instancia guarda los `jti` del tópico hasta su
`expiresAt` y decide con esa lista, siempre que el consumidor esté al día (confirmó con el broker hace menos de 5 s); si
no lo está, responde 401 (falla cerrado). Un token revocado puede servir en ellos el retraso del Outbox más Kafka: unos
2 a 3 s con el Outbox por defecto, 0,57 s medidos con `FIXIA_OUTBOX_RELAY_DELAY_MS=500`.

## Estados replicados (GC-547)

Además de los hechos de negocio, ms-users publica el **estado completo** de técnicos y empresas para que los demás
servicios decidan sin llamarlo. No son hechos de negocio: el lakehouse no los consume. Sobre propio:
`eventId`, `eventType`, `schemaVersion` 1, `occurredAt`, `producer`, `aggregate` (`type`, `id` = entidad, `sequence` =
instante de publicación en ms), `removed` y `data`. Se publican solo cuando cambian y todos de nuevo cada 6 h.

| Estado | Tópico (clave) | `data` |
| --- | --- | --- |
| `tecnico.estado` | `fixia.users.technician-states` (técnico) | `userId`, `active`, `displayName`, `verificationStatus`, `verificationRequirementMet`, `categories`, `professionalDescription`, `yearsOfExperience`, `coverage` (`city`, `latitude`, `longitude`, `radiusKm`), `availability` (`dayOfWeek`, `startTime`, `endTime`), `companyAdminUserId`, `reputation` |
| `empresa.estado` | `fixia.users.company-states` (cuenta de la empresa) | `companyAdminUserId`, `active`, `name`, `reputation` |

Sin correo, teléfono, documento ni NIT. `coverage` revela el domicilio aproximado: solo viaja por tópicos internos. ms-users
copia a su vez `servicio.participacion` y `ejecutor.carga` (ms-services) y `pago.retencion` (ms-payments). Detalle en
`ms-services/docs/spikes/GC-547-desacoplamiento-users-services.md` §9.

## Consumidores

- `ms-intake-lakehouse` lo conserva como cualquier evento, bajo `producer=ms-users/type=evaluacion.registrada/v=1/`.
  Para guardar también los de empresa hay que suscribirlo a `fixia.users.companies` y copiar los dos esquemas nuevos.
- `sesion.revocada`: ms-services, ms-payments y ms-matching-geo, cada instancia con su propio grupo (todas necesitan
  todos los `jti`); al arrancar releen los últimos 20 minutos del tópico. El lakehouse puede ignorarlo.
- Reputación (GC-446): se calcula al consultar en `ms-users` (opción A, implementada) y no consume el evento. Si
  algún día se aprueba una proyección (opción B), la deduplica por `eventId` y aprovecha que la clave del mensaje es
  el evaluado.
