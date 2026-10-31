# Contrato de eventos de negocio de ms-users (GC-443)

Versión 1, Sprint 5. `ms-users` publica un solo hecho, `evaluacion.registrada`, que implementa GC-444. Sigue las
mismas reglas que el contrato de eventos de `ms-services` (GC-436, `ms-services/docs/events/README.md`): eventos
completos sin datos personales, Outbox en la misma transacción, entrega al menos una vez con `eventId` estable y
versionado por tipo. Aquí solo se anota lo que cambia. Las decisiones están en
[`../contracts/evaluaciones.md`](../contracts/evaluaciones.md).

Los esquemas están en [`schemas/`](schemas/) (JSON Schema 2020-12, con sus propios `$id` bajo
`https://fixia.local/schemas/events/ms-users/`) y dos ejemplos en [`examples/`](examples/). Si este texto y un esquema
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

**No viaja el comentario** de la evaluación: puede traer datos personales. Lo consultan solo el evaluador y el evaluado
por la API.

## Consumidores

- `ms-intake-lakehouse` lo conserva como cualquier evento, bajo `producer=ms-users/type=evaluacion.registrada/v=1/`.
- Reputación (GC-446): con la opción recomendada se calcula al consultar en `ms-users` y no consume el evento. Si se
  aprueba una proyección (opción B), la deduplica por `eventId` y aprovecha que la clave del mensaje es el evaluado.
