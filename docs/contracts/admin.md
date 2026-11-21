# Contrato mínimo del ADMIN de Fixia (GC-534)

Tarea GC-534 de la historia GC-519 («Operar administración web mínima y reembolsos autorizados», F9.1). Define qué puede
hacer un administrador de Fixia al cierre del proyecto, qué rutas lo implementan en cada servicio, qué errores devuelven y
qué queda registrado. Lo implementan GC-535 (backend), GC-536 (panel web) y GC-537 (validación), y depende de GC-503
(cuenta ADMIN).

Fuentes: SRS V6 (MF-09, RF-074, RF-078, RF-079, RF-081, IU-04), GC-476/GC-479/GC-487 (billetera y escrow simulados,
reembolso ADMIN), GC-503 (DEF-QA-23) y el código de `ms-users` y `ms-payments` en `develop` al 2026-11-07.

**Fuera de alcance** (lo dice la propia historia): gestión de categorías, disputas, suspensión de cuentas, verificación de
técnicos (sigue simulada), reportes e indicadores. Tampoco hay segundo factor (RF-008).

## 1. Decisiones

| # | Decisión | Por qué |
| --- | --- | --- |
| D1 | El ADMIN es una cuenta de `ms-users` con `role = ADMIN`; su token lleva `roles: ["ROLE_ADMIN"]` | Ya existe `Role.ADMIN` y el claim `roles`; los demás servicios ya leen ese claim |
| D2 | **Ningún registro público crea un ADMIN.** En QA la cuenta sale del seed (`seed/qa_admin.sql`, perfil `seed-qa`, GC-503) | Un rol con poder sobre el dinero no se autoasigna. Fuera de QA no hay ADMIN hasta que se defina cómo crearlo (§7) |
| D3 | Cada servicio expone sus propias rutas `/api/<servicio>/admin/**` sobre **sus datos**; no hay `ms-admin` | Database per Service; GC-535 pide «reutilizar servicios, no leer BD ajena». El gateway ya enruta por prefijo |
| D4 | El ADMIN **consulta** usuarios y pagos y **ejecuta una sola acción**: el reembolso de `ms-payments` (GC-487), que ya existe | Es lo que GC-503 necesita: liberar o devolver el saldo retenido. No se agregan otras acciones |
| D5 | **Toda acción del ADMIN queda registrada** en el servicio dueño del dato, incluidas las consultas | RF-081 (acceso privilegiado a datos personales y movimientos financieros) y RF-079 (responsable y motivo) |
| D6 | El ADMIN **no** usa las rutas de cliente, técnico o empresa (billetera propia, solicitudes, postulaciones) | Ya es así: esas rutas exigen otros roles y responden 403 |
| D7 | Errores con el formato `application/problem+json` que ya usa cada servicio | Sin formato nuevo para el front |

## 2. Acceso

- **Login:** el mismo de todos, `POST /api/users/auth/login`. En QA: `admin@fixia-qa.test` con la contraseña de QA del
  README de `ms-users`.
- **Autorización:** toda ruta `/admin/**` y el reembolso exigen `ROLE_ADMIN`.

| Caso | Respuesta |
| --- | --- |
| Sin token, token vencido o revocado | 401 |
| Token de cliente, técnico o empresa | 403 |
| Token ADMIN y recurso inexistente | 404 |

## 3. `ms-users`: usuarios y roles (GC-535)

### 3.1 `GET /api/users/admin/users`

Lista paginada por cursor, de la más reciente a la más antigua (`createdAt` desc, `id` desc).

| Parámetro | Regla |
| --- | --- |
| `role` | Opcional: `CLIENT`, `PROFESSIONAL`, `COMPANY`, `ADMIN`. Otro valor → 400 |
| `status` | Opcional: `ACTIVE`, `DISABLED`. Otro valor → 400 |
| `q` | Opcional, 2 a 100 caracteres: busca en correo, nombre y apellido sin distinguir mayúsculas. Fuera de rango → 400 |
| `limit` | 1 a 100, por defecto 20. Fuera de rango → 400 |
| `cursor` | El `nextCursor` de la página anterior (opaco). Inválido → 400 |

```json
{
  "items": [
    {
      "id": "7ec00000-0000-4000-8000-000000000001",
      "email": "tecnico1@fixia-qa.test",
      "firstName": "Carlos",
      "lastName": "Giraldo",
      "role": "PROFESSIONAL",
      "status": "ACTIVE",
      "createdAt": "2026-11-04T15:00:00Z"
    }
  ],
  "nextCursor": "eyJ0IjoiMjAyNi0xMS0wNFQxNTowMDowMFoiLCJpIjoiN2VjMC4uLiJ9"
}
```

`nextCursor` es `null` en la última página. La lista **no** trae documento ni teléfono.

### 3.2 `GET /api/users/admin/users/{id}`

Lo de la lista, más `phone`, `documentType` y `documentNumberMasked` (solo los 4 últimos dígitos, p. ej. `"******0001"`).
Si el usuario es técnico o empresa, también trae `verificationStatus` o `companyName`. `id` inexistente → 404.

### 3.3 `GET /api/users/admin/audit-events`

Registro de las acciones del ADMIN en `ms-users` (§5), del más reciente al más antiguo. Admite `adminId`, `action`,
`limit` y `cursor`, con las mismas reglas de §3.1.

## 4. `ms-payments`: pagos y reembolso (GC-535 y GC-487)

### 4.1 `GET /api/payments/admin/payments` (nueva)

Pagos para soporte, paginados por cursor (`createdAt` desc, `id` desc).

| Parámetro | Regla |
| --- | --- |
| `holdStatus` | Opcional: `Pending`, `Held`, `Failed`, `Captured`, `Released`, `Reversed` o `NotApplicable`. Es el `WalletHoldStatus` que ya devuelve `PaymentResponse`, con la misma escritura (los enums de `ms-payments` se serializan con su nombre) |
| `status` | Opcional: `Pending`, `Processing`, `Completed`, `Failed`, `Refunded` |
| `serviceRequestId` | Opcional |
| `userId` | Opcional: pagos donde el usuario es cliente **o** prestador |
| `limit`, `cursor` | Como en §3.1 |

Cada ítem es el `PaymentResponse` actual con desglose, **más** dos campos:

| Campo | Valor |
| --- | --- |
| `refundable` | `true` si hoy procede un reembolso: billetera `Held` o `Captured`, o pasarela `Completed` |
| `refundKind` | Lo que haría el reembolso: `RELEASE` (retenido → vuelve al cliente), `REVERSAL` (cobrado → prestador y Fixia devuelven), `GATEWAY` (pago anterior sin billetera) o `null` |

### 4.2 Consultas de un pago (ya existen)

`GET /api/payments/{id}` y `GET /api/payments/service-request/{serviceRequestId}` ya permiten al ADMIN ver cualquier pago
(`Requester.CanView`). GC-535 solo agrega el registro de §5.

### 4.3 `POST /api/payments/{id}/refund?reason=…` (ya existe, GC-487)

Se conserva el comportamiento implementado y probado:

- Cabecera **`Idempotency-Key`** obligatoria; `reason` obligatorio, máximo 500 caracteres.
- Billetera retenida → **liberación**: el total vuelve al saldo disponible del cliente.
- Billetera cobrada → **reversa** en una sola transacción: el prestador devuelve el precio, Fixia la comisión y el cliente
  recibe el total. Si el prestador ya retiró y no alcanza → **409**, sin cambiar nada.
- Pago sin billetera → reembolso por la pasarela simulada.
- Repetir con la misma clave devuelve la misma respuesta con `Idempotent-Replayed: true`, sin devolver dos veces.
- Ya reembolsado o en un estado que no lo admite → 409. Sin `reason`, `reason` de más de 500 caracteres o sin clave → 400. La misma clave con otro motivo → 422. Pasarela simulada que rechaza → 402. No ADMIN → 403. Pago inexistente → 404.
- Publica `pago.reembolsado` por el Outbox, con `kind` = `RELEASE`, `REVERSAL` o `GATEWAY`.

### 4.4 `GET /api/payments/admin/audit-events`

Igual que §3.3, con las acciones de `ms-payments`.

## 5. Registro de acciones del ADMIN

Cada servicio guarda las acciones de su ADMIN en una tabla propia `admin_audit_events`, en la **misma transacción** que la
acción cuando esta cambia datos (el reembolso):

| Columna | Contenido |
| --- | --- |
| `id` | UUID |
| `admin_id` | `sub` del token (el `users.id` del ADMIN) |
| `action` | `USER_LIST`, `USER_VIEW`, `AUDIT_LIST` (`ms-users`); `PAYMENT_LIST`, `PAYMENT_VIEW`, `PAYMENT_REFUND`, `AUDIT_LIST` (`ms-payments`) |
| `target_type`, `target_id` | `USER` o `PAYMENT` y su id; `null` en las listas |
| `details` | JSON pequeño: filtros usados en una lista, o `reason`, `kind` e `Idempotency-Key` en un reembolso. **Nunca** contraseñas, tokens ni documentos completos |
| `outcome` | `OK`, `DENIED` (403 por regla de negocio), `CONFLICT` (409) o `NOT_FOUND` |
| `occurred_at` | Instante UTC |

- Es **solo de inserción**: no hay ruta para editarla ni borrarla.
- Los 401 y 403 por rol **no** se registran aquí: quien los recibe no es un ADMIN autenticado. Quedan en los logs del gateway.
- La repetición idempotente de un reembolso no crea un segundo `PAYMENT_REFUND`.

## 6. Panel web (GC-536)

- Ruta `/admin` dentro del Flutter web, con guardia por `UserRole.admin`; **no** se incluye en el APK.
- Pantallas mínimas: usuarios (lista con filtros y detalle), pagos (lista con filtros y detalle con desglose),
  reembolso (diálogo con motivo obligatorio y confirmación; el front genera la `Idempotency-Key` una vez por intento y la
  reutiliza en los reintentos) y registro de acciones.
- Muestra los errores del servicio (`title` y `detail`) y nunca ofrece «Reembolsar» si `refundable` es `false`.

## 7. Pendiente de decisión (PO y profesor)

| # | Pregunta | Propuesta |
| --- | --- | --- |
| P1 | La historia GC-534 condiciona el reembolso a «RF-065». En el SRS V6, RF-065 es la confirmación del pago de **comisiones** del modelo anterior (sin billetera). ¿Cuál es la regla vigente en el SRS posterior a V6 (GC-493)? | Tomar como regla la de GC-476/GC-487 de §4.3, ya aprobada por el PO con la billetera simulada |
| P2 | ¿Cómo se crea un ADMIN fuera de QA (demo o producción)? | Variables `FIXIA_ADMIN_EMAIL` y `FIXIA_ADMIN_PASSWORD` desde el secreto del entorno, creando la cuenta al arrancar si no existe. Requiere al DevOps |
| P3 | ¿El ADMIN puede ver el documento completo? | No: solo los 4 últimos dígitos (§3.2) |
| P4 | ¿Cuánto tiempo se conserva `admin_audit_events`? | Sin borrado durante el proyecto |
