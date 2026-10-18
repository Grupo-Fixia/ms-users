# ms-users

Registro de clientes y técnicos, inicio y cierre de sesión, y perfil profesional del técnico
(GC-234 / GC-235 / GC-236 / GC-237). Java 21, Spring Boot 3.2, PostgreSQL.

Contrato de la API: [`docs/openapi.yaml`](docs/openapi.yaml).

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
- La API de consulta y actualización llega con GC-317.

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
- `technicians.version` es un bloqueo optimista: una escritura sobre datos que otra solicitud ya modificó responde
  409 "Modificación concurrente" en vez de pisarla.
