# ms-users

Registro de clientes, inicio y cierre de sesión (GC-234 / GC-236). Java 21, Spring Boot 3.2, PostgreSQL.

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

Sin las dos claves, el servicio **no arranca** (salvo en `local` y `test`). Las claves nunca se guardan en el repositorio.

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
