-- Registro de acciones del ADMIN de Fixia en ms-users (GC-535, contrato docs/contracts/admin.md §5). Solo inserción: ninguna
-- ruta lo edita ni lo borra. Guarda quién (sub del token), qué acción, sobre qué usuario y con qué resultado; los detalles
-- son los filtros usados, nunca contraseñas, tokens ni documentos completos.
CREATE TABLE admin_audit_events (
    id           UUID                     PRIMARY KEY,
    admin_id     UUID                     NOT NULL,
    action       VARCHAR(30)              NOT NULL,
    target_type  VARCHAR(20),
    target_id    UUID,
    details      TEXT,
    outcome      VARCHAR(20)              NOT NULL,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_admin_audit_events_action CHECK (action IN ('USER_LIST', 'USER_VIEW', 'AUDIT_LIST')),
    CONSTRAINT ck_admin_audit_events_outcome CHECK (outcome IN ('OK', 'DENIED', 'CONFLICT', 'NOT_FOUND')),
    CONSTRAINT ck_admin_audit_events_target CHECK ((target_type IS NULL AND target_id IS NULL)
                                                   OR (target_type = 'USER' AND target_id IS NOT NULL))
);

CREATE INDEX idx_admin_audit_events_occurred ON admin_audit_events (occurred_at DESC, id DESC);
CREATE INDEX idx_admin_audit_events_admin ON admin_audit_events (admin_id, occurred_at DESC);
