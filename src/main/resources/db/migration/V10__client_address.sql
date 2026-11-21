-- Dirección del cliente en su perfil (GC-317). Opcional: las cuentas existentes quedan sin dirección. El frontend la
-- usa para proponer la dirección exacta al publicar una solicitud; la solicitud guarda su propia copia en ms-services,
-- así que cambiarla aquí no altera solicitudes ya publicadas. Aplica a cualquier cuenta, pero hoy solo la edita el
-- cliente desde /api/users/clients/me/profile.
ALTER TABLE users ADD COLUMN address VARCHAR(255);
ALTER TABLE users ADD COLUMN city VARCHAR(100);
