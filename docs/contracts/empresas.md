# Contrato de empresas al cierre del proyecto (GC-516)

Historia GC-516 («Gestionar técnicos asociados mediante invitaciones», feature GC-129): GC-523 (invitaciones), GC-524
(aceptar, rechazar y desvincular), GC-525 (Flutter) y GC-526 (pruebas). Este documento **amplía** la sección 8 del
contrato del Sprint 5 (`ms-services/docs/contracts/sprint-5.md`) y no la contradice: lo que no se menciona aquí sigue igual.

Reglas que fijó Tomás O. (PO) el 2026-11-07:

1. Una empresa puede **invitar a técnicos independientes** a unirse a ella.
2. El técnico **acepta o rechaza** la invitación.
3. El técnico puede **desvincularse** siempre que **no tenga trabajos pendientes ni saldos retenidos** con la empresa.
4. La empresa **no** puede desvincular a un técnico.
5. Un técnico asociado **deja de ser independiente**: no se postula por su cuenta mientras pertenezca a la empresa.
6. Una invitación vence a los **7 días**.
7. La vinculación y la desvinculación se publican como **eventos**.
8. Cuando el cliente selecciona a una empresa, **el técnico designado cotiza y ejecuta** el servicio, no la empresa.

Fuentes: SRS V6 (RF-016 a RF-019, RN-015 a RN-018), contrato del Sprint 5 §8, GC-466 y GC-468 (asociación y postulación
empresarial) y el código de `ms-users`, `ms-services` y `ms-payments` en `develop` al 2026-11-07.

## 1. Decisiones

| # | Decisión | Por qué |
| --- | --- | --- |
| D1 | `ms-users` es el dueño de las invitaciones y de la asociación (`company_technicians`) | Ya custodia `companies` y `company_technicians` (GC-466) |
| D2 | Solo se invita a un **técnico independiente**: con cuenta `PROFESSIONAL` activa y sin empresa | Regla 1. Un técnico sigue estando en una sola empresa (`uk_company_technicians_technician`) |
| D3 | Se invita **por correo exacto**. La respuesta no revela si el correo existe ni por qué no se puede invitar | No se exponen cuentas ajenas (RF-017: «sin vinculaciones arbitrarias por id») |
| D4 | La asociación **solo nace de una invitación aceptada** por el técnico | Regla 2. Nadie queda en una empresa sin consentirlo |
| D5 | **Solo el técnico** se desvincula, y solo sin trabajos pendientes ni saldos retenidos con la empresa | Reglas 3 y 4 |
| D6 | Para comprobar la regla 3, `ms-users` pregunta a `ms-services` (trabajo) y a `ms-payments` (dinero) por rutas internas; **si alguno no responde, no se desvincula** (503) | Database per Service: `ms-users` no lee bases ajenas. Falla cerrado, como la elegibilidad (ADR-041) |
| D7 | Cada vinculación y desvinculación queda en un **historial** que no se edita | RF-019 (trazabilidad de la asociación) |
| D8 | Las postulaciones, selecciones y servicios anteriores **siguen siendo de la empresa** aunque el técnico se vaya | El prestador es la empresa (RN-016, RN-017) |
| D9 | Un técnico asociado **no se postula como independiente**: su postulación propia responde 409 y no ve oportunidades propias | Regla 5. Cierra la pregunta abierta del contrato del Sprint 5 §8.1 |
| D10 | Tras seleccionar a una empresa, **el técnico designado** cotiza, inicia, termina y sube evidencias; la empresa **sigue siendo la prestadora** | Regla 8 y RN-017: prestador (empresa) y ejecutor (técnico) son identidades distintas |
| D11 | `ms-users` publica `empresa.tecnico_vinculado` y `empresa.tecnico_desvinculado` por su Outbox | Regla 7. Permite que `ms-services` lleve su propia copia de la asociación (spike GC-547) |

## 2. Qué puede y qué no puede hacer cada uno

| Actor | Puede | No puede |
| --- | --- | --- |
| Empresa | Invitar por correo a un técnico independiente; ver sus invitaciones y su estado; cancelar una invitación pendiente; ver a sus técnicos asociados; ver oportunidades y postularse con uno de sus técnicos (Sprint 5); consultar los servicios en que fue seleccionada (§8) | Vincular a un técnico sin que acepte; invitar a un técnico que ya tiene empresa; tener dos invitaciones pendientes al mismo técnico; **desvincular a un técnico**; ver invitaciones de otra empresa; **cotizar, iniciar, terminar o subir evidencias** (lo hace su técnico) |
| Técnico independiente | Ver las invitaciones pendientes que le llegaron; aceptar una o rechazarla | Aceptar si ya pertenece a una empresa; aceptar una invitación vencida, cancelada o ajena |
| Técnico asociado | Ver a qué empresa pertenece; desvincularse si no tiene trabajos pendientes ni saldos retenidos con la empresa; **cotizar y ejecutar** los servicios en que la empresa fue seleccionada con él como técnico designado | Desvincularse con trabajo pendiente o dinero retenido (409 con el motivo); pertenecer a dos empresas; **postularse como independiente**; cotizar o ejecutar servicios de la empresa asignados a otro técnico |

## 3. Datos (`ms-users`, migración nueva)

Tabla `company_invitations`:

| Columna | Regla |
| --- | --- |
| `id` | UUID, clave primaria |
| `company_id` | FK a `companies` |
| `technician_user_id` | FK a `technicians.user_id` |
| `status` | `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED` |
| `created_at`, `expires_at` | Vence a los **7 días** (configurable con `fixia.companies.invitation-ttl`) |
| `responded_at` | Cuándo pasó a otro estado |
| `cancel_reason` | `BY_COMPANY` o `JOINED_OTHER_COMPANY` |

- Índice único parcial: **una sola invitación `PENDING`** por empresa y técnico.
- **Vencida** = `PENDING` con `expires_at` pasado. Se calcula al leer (se muestra como `EXPIRED`), así que no hace falta
  ningún proceso programado.

Tabla `company_membership_events` (historial, solo inserción): `id`, `company_id`, `technician_user_id`, `type` (`JOINED`
o `LEFT`), `invitation_id` (en `JOINED`) y `occurred_at`.

## 4. API de la empresa (`ROLE_COMPANY`)

### 4.1 `POST /api/users/companies/me/invitations`

```json
{ "technicianEmail": "tecnico5@fixia-qa.test" }
```

- **201** `{id, status: "PENDING", technician: {displayName}, createdAt, expiresAt}`. El nombre se muestra abreviado
  («Carlos G.»), igual que el perfil público.
- **400** correo vacío o mal formado.
- **422** `NOT_INVITABLE`: el correo no existe, no es de un técnico activo o el técnico ya tiene empresa. Es el **mismo
  mensaje** en los tres casos (D3).
- **409** ya hay una invitación `PENDING` de esta empresa a ese técnico.

### 4.2 `GET /api/users/companies/me/invitations?status=`

Invitaciones de la empresa, de la más reciente a la más antigua. `status` es opcional (`PENDING`, `ACCEPTED`, `REJECTED`,
`CANCELLED` o `EXPIRED`).

### 4.3 `DELETE /api/users/companies/me/invitations/{id}`

Cancela una invitación `PENDING`: **204**. Si ya no está pendiente, **409**. Si no existe o es de otra empresa, **404**.

`GET /api/users/companies/me/technicians` (GC-466) no cambia: lista a los asociados actuales.

## 5. API del técnico (`ROLE_PROFESSIONAL`)

### 5.1 `GET /api/users/technicians/me/invitations`

Invitaciones `PENDING` y vigentes que le llegaron, con `{id, company: {name}, createdAt, expiresAt}`.

### 5.2 `POST /api/users/technicians/me/invitations/{id}/accept`

En **una transacción**:

1. Comprueba que la invitación es suya, está `PENDING` y no venció.
2. Crea la fila en `company_technicians`.
3. Marca la invitación `ACCEPTED`.
4. Cancela sus demás invitaciones pendientes con `JOINED_OTHER_COMPANY`.
5. Registra `JOINED` en el historial.

Respuestas:

- **200** `{company: {name}, joinedAt}`.
- **409** si ya pertenece a una empresa (la restricción única también lo resuelve con dos aceptaciones simultáneas) o si
  la invitación ya no está pendiente o venció.
- **404** si no existe o es de otro técnico.

### 5.3 `POST /api/users/technicians/me/invitations/{id}/reject`

**200**, y la invitación pasa a `REJECTED`. **409** si ya no está pendiente.

### 5.4 `GET /api/users/technicians/me/company`

**200** `{company: {name}, joinedAt}` si pertenece a una empresa, o **404** si es independiente.

### 5.5 `DELETE /api/users/technicians/me/company` (desvincularse)

1. `ms-users` pregunta a `ms-services` qué trabajo tiene el técnico con la empresa (§6.1).
2. Con todos los servicios que obtuvo, pregunta a `ms-payments` si hay dinero retenido (§6.2).
3. Si no hay nada, en **una transacción** borra la fila de `company_technicians` y registra `LEFT` en el historial.

Respuestas:

- **204**: queda independiente.
- **409** `{reasons: ["PENDING_WORK", "HELD_BALANCE"], serviceRequestIds: [...]}`, con uno o los dos motivos. No cambia
  nada.
- **404** si ya es independiente.
- **503** si `ms-services` o `ms-payments` no responden. No se desvincula (D6).

## 6. Rutas internas nuevas (`X-Internal-Api-Key`)

### 6.1 `ms-services`: `POST /internal/services/company-executors/workload`

```json
{ "companyAdminUserId": "c0a0…0001", "technicianUserId": "7ec0…0001" }
```

→ `{ "pendingServiceRequestIds": [...], "serviceRequestIds": [...] }`

- **Trabajo pendiente** = postulaciones de esa empresa en las que el técnico es el ejecutor propuesto y que siguen
  vivas:
  - postulación `APPLIED` con la solicitud `PUBLISHED`;
  - o postulación `SELECTED` con la solicitud en `PROVIDER_SELECTED`, `CONTRACTED`, `IN_PROGRESS` o
    `TECHNICIAN_FINISHED`.
- `serviceRequestIds` son **todas** las solicitudes en las que el técnico fue el ejecutor seleccionado de esa empresa,
  incluidas las cerradas. `ms-users` las usa para preguntar por el dinero.

### 6.2 `ms-payments`: `POST /internal/payments/held`

```json
{ "providerId": "c0a0…0001", "serviceRequestIds": ["…"] }
```

→ `{ "heldServiceRequestIds": [...] }`: pagos de esa empresa sobre esas solicitudes con `HoldStatus = Held`, o `Pending`
(registrado y aún sin retener).

- Hace falta porque un pago puede seguir retenido aunque el servicio ya esté cerrado, por ejemplo si la captura falló.
- `ms-payments` no conoce al técnico: el pago está a nombre de la empresa.
- Hasta 500 ids por consulta.

### 6.3 Eventos de `ms-users` (Outbox, tópico `fixia.users.companies`, clave = `technicianUserId`)

Mismo sobre que los demás eventos (`eventId`, `eventType`, `schemaVersion`, `occurredAt`, `producer`, `actor`, `data`),
escritos en la misma transacción que el cambio:

| Evento | Cuándo | `data` |
| --- | --- | --- |
| `empresa.tecnico_vinculado` v1 | Al aceptar una invitación (§5.2) | `companyId`, `companyAdminUserId`, `technicianUserId`, `invitationId`, `joinedAt` |
| `empresa.tecnico_desvinculado` v1 | Al desvincularse (§5.5) | `companyId`, `companyAdminUserId`, `technicianUserId`, `leftAt` |

Sin correo, nombre ni documento. Los esquemas JSON van en `docs/events` con sus ejemplos.

## 7. Efectos en el resto del sistema

- **Ver oportunidades y postularse por la empresa:** solo con técnicos asociados **en ese momento**. Ya es así:
  `ms-services` consulta la asociación en cada postulación (GC-468).
- **Seleccionar, cotizar o contratar** una postulación cuyo técnico ya no está en la empresa: `ms-services` vuelve a
  comprobar la asociación y responde **409** «El técnico propuesto ya no pertenece a la empresa». Esto cubre la carrera
  entre la comprobación de §5.5 y una postulación nueva.
- **Postulación propia de un técnico asociado:** `POST /api/services/applications` como `ROLE_PROFESSIONAL` responde
  **409** «Perteneces a una empresa: te postulas a través de ella», y `GET /api/services/opportunities` le devuelve una
  página vacía (D9). `ms-services` lo sabe por la elegibilidad que ya consulta a `ms-users`, que agrega el motivo
  `COMPANY_MEMBER`.
- **Reputación de la empresa** (decisión del 2026-11-06): promedio simple de las reputaciones de sus técnicos asociados
  **actuales** que tengan evaluaciones. Si un técnico se va, deja de contar; sus evaluaciones siguen siendo suyas.
- **Datos de QA:** las asociaciones del seed se conservan. Para probar invitaciones hace falta un técnico independiente
  en el seed (`tecnico5@fixia-qa.test`, sin empresa).

## 8. Después de la selección: el técnico designado cotiza y ejecuta (`ms-services`, regla 8)

Reemplaza el paso 9 de GC-468 en el contrato del Sprint 5 §8.2, donde la empresa recibía 403 y el técnico 409.

| Paso | Quién | Regla |
| --- | --- | --- |
| Cotizar y versionar | El **técnico designado** (`ROLE_PROFESSIONAL` con `userId = technicianId` de la postulación seleccionada) | La cotización queda con `providerId` = la empresa y `authorId` = el técnico. Antes de cada envío se comprueba que siga asociado (§7) |
| Aceptar la cotización (contratar) | El cliente | Igual que hoy. El contrato guarda `providerId` = la empresa y `executorId` = el técnico |
| Pago retenido y cobrado | `ms-payments` | `servicio.contratado` sigue con `providerId` = la empresa, que recibe el pago, y agrega `executorId` opcional. Sin cambios en `ms-payments` (P6) |
| Iniciar, terminar, subir evidencias | El **técnico designado** | Se autoriza contra `executorId`, no contra el prestador |
| Consultar el servicio y sus evidencias | El técnico designado, la empresa y el cliente | La empresa solo consulta |
| Evaluar | Cliente → **técnico designado**; técnico designado → cliente | `GET /internal/services/{id}/participation` devuelve también `executorId`. `ms-users` evalúa al ejecutor y deja de responder `COMPANY_SERVICE_NOT_EVALUABLE` |
| Historial | El técnico ve los servicios que ejecutó; la empresa, aquellos en que fue seleccionada | `GET /api/services/history` acepta `ROLE_COMPANY` |

- La empresa recibe **403** en cotizar, iniciar, terminar y subir evidencias.
- Otro técnico de la misma empresa recibe **409** («No eres el técnico designado»).
- Eventos: `cotizacion.enviada`, `servicio.iniciado` y `servicio.finalizado` llevan `actor` = el técnico y `providerId` = la
  empresa. Son campos opcionales en v1.

## 9. Decisiones del PO (Tomás O., 2026-11-07)

| # | Pregunta | Decisión |
| --- | --- | --- |
| P1 | ¿La empresa puede desvincular a un técnico? | **No.** Solo el técnico se desvincula (§5.5) |
| P2 | ¿Un técnico asociado puede postularse como independiente? | **No** (D9) |
| P3 | ¿Cuánto dura una invitación? | **7 días**, configurable |
| P4 | ¿Se publican eventos de vinculación y desvinculación? | **Sí** (§6.3) |
| P5 | ¿La empresa cotiza, contrata y ejecuta? | **No: lo hace el técnico designado** (§8) |
| P6 | ¿Quién recibe el pago de un servicio empresarial? | **La empresa**, que es la prestadora (RN-016). El reparto con el técnico es interno de la empresa y queda fuera de Fixia. `ms-payments` no cambia |
