# ms-users

Registro de clientes y técnicos, inicio y cierre de sesión, y perfil profesional del técnico
(GC-234 / GC-235 / GC-236 / GC-237). Java 21, Spring Boot 3.2, PostgreSQL.

Contrato de la API: [`docs/openapi.yaml`](docs/openapi.yaml). Evaluaciones y reputación (GC-443, se implementan en
GC-444 y GC-446): [`docs/contracts/evaluaciones.md`](docs/contracts/evaluaciones.md), con su evento en
[`docs/events`](docs/events/README.md).

## Pruebas

```bash
mvn verify        # pruebas (H2 en memoria) + cobertura mínima de líneas del 80 % (JaCoCo)
```

No necesita Docker: las pruebas usan H2 en modo PostgreSQL con las mismas migraciones de Flyway
(`src/main/resources/db/migration`).

## Ejecutar sin Docker (perfil `local`)

```bash
mvn spring-boot:run -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.profiles=local
```

Usa H2 en memoria y un par de claves JWT efímero (los tokens dejan de valer al reiniciar).
`useTestClasspath` es necesario porque H2 no viaja en el jar de producción.

Para tener también las empresas y técnicos de QA (ver «Empresas»), sumar el perfil `seed-qa`:
`-Dspring-boot.run.profiles=local,seed-qa`.

## Configuración

| Variable | Descripción | Por defecto |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Conexión a PostgreSQL | `localhost:5432/ms-users` |
| `JWT_PRIVATE_KEY` | Clave privada RSA en PEM (PKCS#8), para firmar | **obligatoria** fuera de `local` y `test` |
| `JWT_PUBLIC_KEY` | Clave pública RSA en PEM (X.509), para verificar | **obligatoria** fuera de `local` y `test` |
| `JWT_ISSUER` / `JWT_AUDIENCE` | Claims `iss` y `aud` | `https://auth.fixia.com` / `https://api.fixia.com` |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | Vida de los tokens | `15m` / `7d` |
| `INTERNAL_API_KEY` | Clave compartida para `/internal/**` (consulta de revocación de otros servicios) | vacía: esas rutas rechazan todo |

Sin las dos claves JWT, el servicio **no arranca** (salvo en `local` y `test`). Las claves nunca se guardan en el repositorio.

## Rutas internas

`GET /internal/tokens/{jti}/status` responde `{"revoked": true|false}`. Lo consultan otros microservicios (ms-services)
para saber si un access token fue invalidado por un cierre de sesión, porque la lista de revocados vive en la base de
este servicio. Está fuera de `/api/users` (Traefik y el Ingress no la exponen) y exige la cabecera
`X-Internal-Api-Key` con el valor de `INTERNAL_API_KEY`. Ver `docs/openapi.yaml`.

Generar un par y dejarlo como variables de una sola línea (los saltos de línea se escriben como `\n`, formato que el
servicio acepta tal cual):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/jwt-private.pem
openssl pkey -in /tmp/jwt-private.pem -pubout -out /tmp/jwt-public.pem
printf 'JWT_PRIVATE_KEY="%s"\n' "$(awk 'NF{printf "%s\\n",$0}' /tmp/jwt-private.pem)"
printf 'JWT_PUBLIC_KEY="%s"\n'  "$(awk 'NF{printf "%s\\n",$0}' /tmp/jwt-public.pem)"
rm /tmp/jwt-private.pem /tmp/jwt-public.pem
```

## Sesión

- Access token JWT RS256 de vida corta. Un refresh token de un solo uso se guarda **solo como hash SHA-256**.
- El cierre de sesión invalida el access token en el acto (lista de `jti` revocados) y revoca el refresh token.
- Un job borra cada hora los tokens ya expirados.

## Perfil del cliente

- El perfil del cliente **no tiene tabla propia**: son los datos de la cuenta en `users` (GC-318). Así editarlo no
  puede desalinearse de la cuenta y no hay datos duplicados.
- Editables por el titular: **nombre, apellido y teléfono** (`User#updateContactInfo`). El correo (es el login y
  requeriría verificación) y el documento (identidad única) son de solo lectura; el rol, el estado, la contraseña y
  el consentimiento tienen sus propios flujos.
- Los valores se guardan sin espacios sobrantes; el teléfono admite `+` opcional y de 7 a 15 dígitos
  (`User.PHONE_REGEX`). No hay migración: las columnas ya existen.
- `UserRepository#findByIdAndRole` permite obtener la cuenta solo si es del rol esperado (un técnico no entra por la
  ruta de cliente).
- La edición es de último escritor gana (sin `@Version`): son tres campos del propio titular.
- **API (GC-317).** `GET` y `PUT /api/users/clients/me/profile`, solo con rol `CLIENT` (un técnico recibe 403, igual
  que un cliente en las rutas de técnico). El cliente sale siempre del token, nunca de la ruta ni del cuerpo: nadie
  puede ver ni editar el perfil de otro. `PUT` reemplaza nombre, apellido y teléfono (mismas reglas que el registro:
  nombre y apellido se guardan sin espacios sobrantes; un teléfono con espacios se rechaza);
  si algún dato es inválido responde 400 y la cuenta no cambia. Cualquier otro campo del cuerpo (correo, documento,
  rol, estado) se ignora. `GET` devuelve `id`, `email`, `firstName`, `lastName`, `documentType`, `documentNumber`,
  `phone` y `createdAt`; nunca el hash de la contraseña, el rol, el estado ni el consentimiento. Si la cuenta ya no
  existe, está deshabilitada o la sesión se cerró, la respuesta es el mismo 401 de cualquier token inválido. Los
  DTO no imprimen datos personales en `toString()`.

## Técnicos

- `POST /api/users/technicians` crea la cuenta con rol `PROFESSIONAL` y su fila en `technicians` (verificación `PENDING`)
  en la misma transacción. Pide los mismos datos y el mismo consentimiento que el registro de cliente.
- `GET` / `PUT /api/users/technicians/me/profile` consultan y reemplazan la información profesional. Solo con rol
  `PROFESSIONAL`; el técnico siempre sale del token, así que nadie puede tocar el perfil de otro. Un cliente recibe 403.
- `GET` / `PUT /api/users/technicians/me/categories` consultan y reemplazan solo las especialidades y servicios del
  técnico (RF-010), sin tocar el resto del perfil. `GET /api/users/service-categories` devuelve el catálogo habilitado
  a cualquier usuario autenticado.
- `GET` / `PUT` / `DELETE /api/users/technicians/me/coverage-zone` consultan, reemplazan y retiran la zona de atención
  (ciudad obligatoria y círculo opcional). `GET` / `PUT /api/users/technicians/me/availability` consultan y reemplazan
  la disponibilidad semanal (franjas en hora de Colombia, sin cruzarse; las horas van como `"08:00"`). Solo con rol
  `PROFESSIONAL`; un dato que incumple una regla responde 400.
- Las escrituras del perfil bloquean la fila del técnico: dos actualizaciones simultáneas se aplican una tras otra
  en vez de chocar (DEF-QA-02).
- Persistencia relacional (PostgreSQL), según el DD V2: `technicians` es la tabla TECNICO (especialización 1:1 de
  `users`) y `technician_categories` es TECNICO_CATEGORIA. Este servicio no usa MongoDB: el DD no le asigna ninguna
  colección.
- Perfil profesional (RF-009): descripción, años de experiencia y categorías (`PLUMBING`, `ELECTRICAL`,
  `MAINTENANCE`, `LOCKSMITHING`, `PAINTING`, `CARPENTRY`). Un técnico no puede repetir una categoría.
- Especialidades y servicios ofrecidos (RF-010): son esas mismas categorías, en un solo nivel. La base rechaza
  cualquier valor fuera del catálogo, así que agregar una categoría exige una migración que amplíe la restricción.
- Disponibilidad y zona de atención (RF-011), por ahora solo persistencia, con la misma forma que la solicitud de
  ms-services:
  - Zona en `technicians`: ciudad obligatoria (`coverage_city`) y, si se quiere precisar, un círculo
    (`coverage_latitude`, `coverage_longitude` redondeadas a dos decimales y `coverage_radius_km` de 1 a 100; las tres
    van juntas). `CoverageZone#covers` aplica la regla: misma ciudad y, si hay círculo y la solicitud trae
    coordenadas, dentro del radio.
  - Disponibilidad en `technician_availability`: franjas semanales (día, hora de inicio y hora de fin) en hora de
    Colombia, varias por día y sin cruzarse. `Technician#isAvailableAt` compara el instante esperado de la solicitud.
- **Elegibilidad para otros servicios (GC-342):** `POST /internal/technicians/{userId}/eligibility` recibe una o varias
  solicitudes (categoría, ciudad, coordenadas aproximadas y momento esperado) y responde por cada una si el técnico es
  elegible y, si no, los motivos (`TECHNICIAN_NOT_FOUND`, `CATEGORY`, `ZONE`, `AVAILABILITY`, `VERIFICATION`). La usa
  ms-services al postular (GC-342) y al listar solicitudes compatibles (GC-337). Va bajo `/internal/**` para que la
  proteja la clave compartida de la consulta de revocación (GC-333). La verificación vigente solo se exige con
  `fixia.eligibility.require-verified=true` (por defecto `false`: aún no hay flujo de verificación, RN-001).
- **Perfil público para otros servicios (GC-425):** `POST /internal/technicians/public-profiles` recibe hasta 100
  `userIds` y responde, en el mismo orden, el perfil público de cada técnico activo: `displayName` (nombre e inicial
  del apellido), `verificationStatus`, `categories`, `professionalDescription`, `yearsOfExperience` y `city`. Los ids
  que no son de un técnico activo se omiten. Nunca incluye correo, teléfono, documento ni el centro y el radio de la
  zona. La usa ms-services para mostrarle al cliente quién se postuló a su solicitud. Misma clave interna.
- `technicians.version` es un bloqueo optimista: una escritura sobre datos que otra solicitud ya modificó responde
  409 "Modificación concurrente" en vez de pisarla.

## Empresas

Identidad empresarial y técnicos asociados (GC-466, historia GC-464).

- **Identidad.** Una empresa es una cuenta con rol `COMPANY` (distinto de `ADMIN`, que queda para la plataforma) más una
  fila en `companies` (`admin_user_id` único, nombre y NIT opcional). Usa el mismo inicio de sesión y el mismo token que
  el resto de los roles. La empresa se resuelve siempre por el `sub` del token; ninguna ruta recibe un id de empresa.
- **Asociaciones.** `company_technicians` une una empresa con la cuenta de un técnico existente. Un técnico solo puede
  estar en **una** empresa (`uk_company_technicians_technician`) y debe tener perfil de técnico (clave foránea a
  `technicians.user_id`). No hay indicador de habilitación: si la cuenta del técnico se deshabilita, deja de aparecer.
- **No hay forma de asociar por API.** Sin registro público de empresas, sin invitaciones ni ruta que vincule por id; así
  nadie reclama una cuenta ajena por conocer su id. Las empresas y asociaciones se cargan de forma controlada.
- **API (GC-466).** `GET /api/users/companies/me` y `GET /api/users/companies/me/technicians`, solo con rol `COMPANY`
  (cliente y técnico reciben 403). El listado devuelve los mismos campos permitidos del perfil público (GC-425) y su
  `userId` es el que ms-services usa para postular y seleccionar al ejecutor; sin técnicos, lista vacía.
- **Verificación para ms-services (GC-468).** `POST /internal/companies/technician-association` con
  `{companyAdminUserId, technicianUserId}` responde `{associated: true|false}`. Siempre 200 y con la misma forma: ningún
  motivo de «no» se distingue de otro. Misma clave interna. Lo usa ms-services al recibir la postulación de una
  empresa (GC-468).
- **Nombre de las empresas para ms-services (GC-468).** `POST /internal/companies/public-profiles` con
  `{companyAdminUserIds}` (hasta 100) responde `{profiles: [{companyAdminUserId, name}]}`, en el orden pedido y sin
  repetidos, solo para cuentas `COMPANY` activas con empresa cargada; las demás se omiten. Nunca el NIT ni los datos de
  la cuenta. ms-services lo muestra como `provider` en los postulantes de una solicitud. Misma clave interna.
- **Técnicos de la empresa para ms-services (GC-468).** `POST /internal/companies/technicians` con
  `{companyAdminUserId}` responde `{technicianUserIds: [...]}`: los técnicos activos de esa empresa, en el orden en que
  se asociaron. Si la cuenta no es de una empresa activa, o la empresa no tiene técnicos, la lista sale vacía y siempre
  es 200. ms-services la usa para mostrarle a la empresa las solicitudes que puede atender y con cuál de sus técnicos
  (`eligibleTechnicianIds`). Misma clave interna; no se cachea.
- **Qué hay disponible para verificar elegibilidad.** Estado de verificación, especialidades, experiencia y ciudad en el
  listado; disponibilidad y círculo de cobertura, en la consulta de elegibilidad por técnico. No se añaden reglas nuevas
  de forma implícita.

### Datos de QA (perfil `seed-qa`)

`seed/qa_empresas.sql` es un script **idempotente** que carga `QaSeedLoader` al arrancar, solo con el perfil `seed-qa`
y después de las migraciones. Nunca se activa en producción: crea cuentas con una contraseña conocida. En Docker,
`SPRING_PROFILES_ACTIVE=seed-qa` en el servicio `ms-users` del entorno.

- **No es una migración de Flyway** y no deja fila en `flyway_schema_history`. Por eso quitar el perfil es seguro: el
  servicio arranca igual y las cuentas ya cargadas se conservan. (Una versión anterior lo cargaba como migración repetible;
  eso impedía arrancar sin el perfil: «Detected applied migration not resolved locally».)
- **Bases que ya aplicaron la versión anterior.** Si un entorno con PostgreSQL llegó a arrancar con `seed-qa` antes de este
  cambio, `flyway_schema_history` conserva la fila `qa seed empresas` y el servicio no inicia hasta borrarla (una sola vez):
  `DELETE FROM flyway_schema_history WHERE description = 'qa seed empresas';`. Las cuentas de QA no se tocan. Con H2 en
  memoria (perfil `local`) no hay nada que limpiar.
- Si una sentencia del script falla, se revierte todo y el servicio no arranca, en vez de dejar datos a medias.

| Cuenta | Rol | Contenido |
|---|---|---|
| `empresa1@fixia-qa.test` | `COMPANY` | Servicios Integrales Andina: `tecnico1`, `tecnico2` |
| `empresa2@fixia-qa.test` | `COMPANY` | Mantenimientos del Valle: `tecnico3`, `tecnico4` |
| `empresa3@fixia-qa.test` | `COMPANY` | Hogar Experto, sin técnicos (lista vacía) |
| `tecnico1..4@fixia-qa.test` | `PROFESSIONAL` | Verificados, con especialidades y ciudad de cobertura |

Contraseña de todas las cuentas de QA: `FixiaQA#2026`.
