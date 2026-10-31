# Contrato de evaluaciones y reputación (GC-443)

Spike del Sprint 5, historia GC-412. Define cómo se implementan las evaluaciones bidireccionales (GC-444) y qué recibe
la reputación (GC-446): dónde se guardan, cómo `ms-users` comprueba que el servicio está cerrado sin leer la base de
`ms-services`, qué API se expone, cómo se comporta ante reintentos y concurrencia, y qué evento se publica.

Fuentes: SRS V6 (RF-051 a RF-056, RN-012, RN-017, CA-30, CA-31, §6.10), DD V4 (CD-08), contrato de eventos de
`ms-services` (GC-436) y contratos del Sprint 5 (`ms-services/docs/contracts/sprint-5.md`, GC-454, §7 y §9).

## 1. Decisiones

| # | Decisión | Por qué |
| --- | --- | --- |
| D1 | `ms-users` es el **custodio** de las evaluaciones originales (tabla propia, migración V9) | Decisión del equipo (SRS V6 §6.10); no se vuelve a discutir. Reemplaza el CD-08 del DD V4, que las embebía en la colección `Post` de MongoDB |
| D2 | `ms-users` verifica el cierre **preguntando a `ms-services`** por una ruta interna, nunca leyendo su base | Database per Service (DD V4 §3.2). La misma clave interna que ya usan los dos (`INTERNAL_API_KEY`) |
| D3 | La **dirección y el evaluado se deducen** del token y de la participación; el cuerpo no los trae | Nadie puede evaluar a quien no fue su contraparte ni elegir la dirección |
| D4 | **Una evaluación por servicio y dirección**, garantizada por la base | RN-012, RF-053. La restricción única resuelve también la concurrencia |
| D5 | **Inmutables**: no se editan ni se borran en este sprint | DD V4 CD-08: «toda corrección es un evento nuevo». Corregir queda para cuando existan disputas |
| D6 | Solo servicios con **técnico independiente**. Los servicios de una empresa no se evalúan | SRS V6 §6.10 y RN-017: el flujo empresarial termina en la selección (ALC-08) |
| D7 | Plazo para evaluar: **30 días desde el cierre**, configurable | RF-053 pide «dentro del plazo configurado» |
| D8 | El hecho se publica como `evaluacion.registrada` desde un **Outbox propio de `ms-users`**, con la misma tabla que el de `ms-services` | RF-054 y SRS §6.10 (Outbox en la misma transacción). Misma forma para que el relay de GC-434 sirva para los dos |
| D9 | Reputación: **calcular al consultar** a partir de las evaluaciones (recomendado); la proyección por eventos queda pendiente de aprobación | Ver sección 7. Sin copia que se desincronice (RF-054, CA-30) |

## 2. Datos (`ms-users`, migración V9)

Tabla `service_evaluations`:

| Columna | Tipo | Regla |
| --- | --- | --- |
| `id` | UUID | Clave primaria |
| `service_request_id` | UUID | Id de la solicitud en `ms-services`. Sin clave foránea: vive en otra base |
| `direction` | VARCHAR(30) | `CLIENT_TO_PROVIDER` o `PROVIDER_TO_CLIENT` |
| `evaluator_id` | UUID | `users.id` de quien evalúa (FK a `users`) |
| `evaluated_id` | UUID | `users.id` de quien recibe la evaluación (FK a `users`) |
| `rating` | SMALLINT | 1 a 5 |
| `comment` | VARCHAR(500) | Opcional. Sin espacios a los lados; vacío se guarda como `NULL` |
| `source` | VARCHAR(30) | `CONFIRMED_CLOSURE` (cliente confirmó el cierre). Reservado `ADMINISTRATIVE_RESOLUTION` para cuando existan disputas (RF-055) |
| `service_closed_at` | TIMESTAMPTZ | Momento del cierre según `ms-services`, guardado como prueba de que se evaluó un servicio cerrado |
| `created_at` | TIMESTAMPTZ | Momento de la evaluación |

Restricciones:

- `uk_service_evaluations_direction UNIQUE (service_request_id, direction)`: una por servicio y dirección (D4).
- `CHECK (rating BETWEEN 1 AND 5)`, `CHECK (direction IN (...))`, `CHECK (source IN ('CONFIRMED_CLOSURE'))` (se
  amplía con una migración cuando exista la resolución administrativa) y `CHECK (evaluator_id <> evaluated_id)`.
- Índice `(evaluated_id, direction)` para la reputación (sección 7) e índice `(evaluator_id)` para «mis evaluaciones».

No se guarda el nombre de nadie ni datos del servicio: solo identificadores. El perfil se consulta como siempre.

## 3. Verificación del cierre (`ms-services`, ruta interna nueva)

```
GET /internal/services/{serviceRequestId}/participation
X-Internal-Api-Key: <INTERNAL_API_KEY>
```

`ms-services` no tiene rutas `/internal/**` todavía: se agrega un filtro de clave interna con el mismo patrón que
`ms-users` (`InternalApiKeyFilter`): sin clave o con una clave equivocada, 401; sin clave configurada, rechaza todo. La
ruta no pasa por el gateway ni por el Ingress.

Respuesta 200:

```json
{
  "serviceRequestId": "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7",
  "status": "CLOSED",
  "clientId": "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
  "providerId": "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f",
  "providerType": "INDEPENDENT",
  "closedAt": "2026-10-29T10:00:00Z"
}
```

- `providerId` es el prestador seleccionado (`null` si todavía no hay); `providerType`, `INDEPENDENT` o `COMPANY`
  (`null` sin prestador).
- `closedAt` sale del historial de transiciones (el paso a `CLOSED`, igual que en el historial de GC-448); `null` si
  no está cerrado.
- 404 si la solicitud no existe. No trae dirección, precio, descripción ni datos personales: solo lo necesario para
  decidir.
- `Cache-Control: no-store`. `ms-users` no guarda la respuesta: pregunta en cada evaluación, así un cierre tardío
  (el servicio se cierra después del primer intento) se reconoce en el siguiente.

## 4. API de `ms-users`

### 4.1 Registrar: `POST /api/users/evaluations`

Roles `ROLE_CLIENT` y `ROLE_PROFESSIONAL` (una empresa recibe 403).

```json
{ "serviceRequestId": "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7", "rating": 5, "comment": "Puntual y ordenado" }
```

Pasos, en este orden:

1. Validar el cuerpo: `serviceRequestId` obligatorio, `rating` entero de 1 a 5, `comment` opcional de hasta 500. Si
   no, **400**.
2. Consultar la participación (sección 3). Si `ms-services` no responde o responde algo inválido, **503** (falla
   cerrado: sin verificación no se evalúa).
3. Deducir la dirección (D3):
   - el que llama es `clientId` y tiene `ROLE_CLIENT` → `CLIENT_TO_PROVIDER`, evaluado `providerId`;
   - el que llama es `providerId` y tiene `ROLE_PROFESSIONAL` → `PROVIDER_TO_CLIENT`, evaluado `clientId`;
   - si la solicitud no existe (404) o quien llama no participó, **404** «Servicio no encontrado»: un tercero no
     aprende si el servicio existe.
4. Reglas, con `reasons` en la respuesta **422**:
   - `SERVICE_NOT_CLOSED`: el estado no es `CLOSED`;
   - `COMPANY_SERVICE_NOT_EVALUABLE`: `providerType` es `COMPANY` (D6);
   - `EVALUATION_WINDOW_EXPIRED`: pasaron más de `fixia.evaluations.window` (por defecto `P30D`, variable
     `EVALUATIONS_WINDOW`) desde `closedAt` (D7).
5. Guardar la evaluación y su evento en la **misma transacción** (sección 6).

Respuestas:

| Caso | Código | Cuerpo |
| --- | --- | --- |
| Creada | **201** | La evaluación (abajo) |
| Repetida igual (mismo autor, servicio, nota y comentario) | **200** | La evaluación existente; no se publica otro evento |
| Ya existe otra distinta en esa dirección | **409** | `reasons: ["EVALUATION_ALREADY_EXISTS"]` |
| Dos envíos a la vez | Uno gana (201); el otro choca con la restricción única, relee y responde 200 o 409 con la misma regla | |

```json
{
  "id": "3e4f5a6b-7c8d-4e9f-8a0b-1c2d3e4f5a6b",
  "serviceRequestId": "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7",
  "direction": "CLIENT_TO_PROVIDER",
  "evaluatorId": "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
  "evaluatedId": "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f",
  "rating": 5,
  "comment": "Puntual y ordenado",
  "source": "CONFIRMED_CLOSURE",
  "createdAt": "2026-10-30T15:00:00Z"
}
```

### 4.2 Consultar: `GET /api/users/evaluations?serviceRequestIds=a,b,c`

Roles `ROLE_CLIENT` y `ROLE_PROFESSIONAL`. De 1 a 50 ids (400 si no). Devuelve `{ "items": [...] }` con las
evaluaciones de esos servicios **en las que quien consulta es el evaluador o el evaluado**, con la forma de 4.1. Las de
servicios ajenos simplemente no aparecen. No consulta a `ms-services`.

Sirve para las dos pantallas: el detalle de un servicio (un id) y el historial propio de GC-449, que ya tiene los ids
de sus servicios por `GET /api/services/history` y pide aquí sus evaluaciones en un solo llamado. Reemplaza las dos
formas que aparecían en el contrato del Sprint 5 (`?serviceRequestId=` en §7 y `/mine?serviceRequestIds=` en §9).

### 4.3 Lo que no hay en este sprint

Editar, borrar, responder una evaluación, evaluar a una empresa y listar comentarios en el perfil público. El perfil
público solo muestra la reputación agregada (sección 7).

## 5. Seguridad y privacidad

- El comentario lo ven solo el evaluador y el evaluado (4.2). No viaja en el evento ni en el perfil público: puede traer
  datos personales.
- Los ids siempre salen del token (`sub` sin `usr_`), nunca del cuerpo.
- La ruta interna de `ms-services` expone estado, ids y fecha de cierre; nada más.

## 6. Evento `evaluacion.registrada` (versión 1)

Esquemas en [`../events/`](../events/README.md). Resumen:

- **Outbox de `ms-users`:** tabla `outbox_events` con las mismas columnas que la de `ms-services` (V7 de ese
  servicio), en la migración V9 o V10 de `ms-users`. La evaluación y su evento se guardan en la misma transacción; si
  la transacción se revierte, no queda evento. El relay de GC-434 lee esta tabla igual que la de `ms-services` (otra
  conexión, mismo código).
- **Tópico:** `fixia.users.evaluations`. **Clave del mensaje: `evaluatedId`.** La reputación es por persona evaluada,
  así que todas sus evaluaciones caen en la misma partición y se leen en orden; un consumidor de reputación no tiene que
  reordenar.
- **Sobre:** el mismo de GC-436 con `producer: "ms-users"`, `aggregate = {type: "Evaluation", id: <evaluationId>,
  sequence: 1}` (una evaluación es un hecho único e inmutable, su historia tiene un solo paso) y `correlationId =
  serviceRequestId`, que la enlaza con los eventos del servicio en el lakehouse.
- **Actor:** quien evalúa, con rol `CLIENT` o `PROFESSIONAL`.
- **Datos:** `evaluationId`, `serviceRequestId`, `direction`, `evaluatorId`, `evaluatedId`, `rating`, `source` y
  `serviceClosedAt`. **Sin el comentario.**
- Solo se publica al crear. La repetición idéntica (200) no publica nada.
- El lakehouse lo guarda como cualquier otro evento, bajo `producer=ms-users`.

## 7. Reputación (GC-446, a cargo de Juan Pablo)

Separado a propósito: el criterio de GC-443 pide señalar aparte cualquier destino de proyección que no esté aprobado.

**Regla, igual en cualquier opción:** la reputación de un técnico es el promedio y el número de sus evaluaciones
`CLIENT_TO_PROVIDER`. Promedio redondeado a un decimal (mitad hacia arriba). Sin evaluaciones, `null`: nunca
estrellas inventadas (RF-054). La del cliente (`PROVIDER_TO_CLIENT`) se calcula igual y se guarda, pero no se muestra
en ninguna pantalla en este sprint.

**Dónde se muestra:** en el perfil público que ya existe, `reputation: {"average": 4.5, "count": 12}` o `null`, en
`POST /internal/technicians/public-profiles` (lo usan los postulantes de GC-425, que muestra GC-447) y en el perfil
del técnico.

| Opción | Cómo | A favor | En contra |
| --- | --- | --- | --- |
| **A. Calcular al consultar (recomendada)** | `AVG` y `COUNT` sobre `service_evaluations` por `evaluated_id` al armar el perfil público, con el índice de la sección 2 | Siempre coincide con la fuente; nada que deduplicar ni reconstruir; cabe en el sprint | Una consulta agregada por cada perfil pedido (por lotes de hasta 100, una sola consulta `GROUP BY`) |
| B. Proyección por eventos | Un consumidor de `evaluacion.registrada` mantiene una tabla de reputación, deduplicando por `eventId` | Lectura más barata si la carga crece | Copia que hay que reconstruir; contradice «no hay otra base que sincronizar» (GC-436 §1) y necesita aprobación en el SAD/DD |

Con la opción A los criterios de GC-446 se cumplen así: no se duplica nada porque no hay copia, una reentrega del
evento no cambia nada porque el cálculo no lee eventos, y «reconstruir» es volver a consultar. Si más adelante se elige
B, el evento de la sección 6 ya trae lo necesario (`eventId`, `evaluatedId`, `rating`, `direction`) y la clave del
mensaje asegura el orden por persona.

## 8. Pruebas que deben existir

**GC-444 (`ms-users` y `ms-services`):**

- Ruta interna: 401 sin clave, 404 si no existe, cada estado, `closedAt` del historial, servicio de empresa.
- Ambas direcciones; un tercero recibe 404; una empresa recibe 403.
- 422 por servicio no cerrado, por empresa y por plazo vencido; 400 por nota 0, 6, decimal o comentario largo.
- Repetición idéntica 200 sin segundo evento; distinta 409; dos envíos simultáneos dejan una sola fila y un solo
  evento.
- 503 si `ms-services` no responde; nada queda guardado.
- El evento cumple el esquema y no trae el comentario; la evaluación y el evento se revierten juntos.
- Punta a punta: cerrar un servicio real (GC-441) y evaluarlo en las dos direcciones.

**GC-446:** sin evaluaciones (`null`), primera evaluación, varias (promedio y redondeo), las de la otra dirección no
cuentan, y si se elige la opción B: reentrega del mismo `eventId`, orden alterado y reconstrucción desde la tabla.

## 9. Texto para el DD y el SDD (lo consolida GC-455)

- **DD, CD-08 Reputación:** «Custodio: `ms-users` (tabla `service_evaluations`, Sprint 5). Una evaluación por servicio
  y dirección (RN-012), escala 1–5, inmutable. La elegibilidad se verifica con `ms-services` por la ruta interna
  `GET /internal/services/{id}/participation`, sin acceso a su base. La reputación se calcula al consultar a partir de
  las evaluaciones originales.» Reemplaza la colección `Post` de MongoDB y «Calificaciones y Métricas».
- **SDD:** `ms-users` pasa a publicar eventos: `evaluacion.registrada` (v1) por su Outbox en `fixia.users.evaluations`.
  `ms-services` expone su primera ruta interna, con la clave compartida (ADR-040).

## 10. Pendiente fuera de este contrato

- Elegir entre las opciones A y B de la reputación (Juan Pablo, GC-446).
- Que el relay de GC-434 lea también el Outbox de `ms-users` y que el tópico `fixia.users.evaluations` exista en QA
  (DevOps, GC-418/456).
- Plazo como parámetro versionado con vigencia (RNF-019): por ahora es configuración.
