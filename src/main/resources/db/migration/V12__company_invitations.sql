-- Invitaciones de una empresa a un técnico independiente (GC-523, docs/contracts/empresas.md §3).
--
-- La asociación (company_technicians) solo nace cuando el técnico acepta (GC-524); aquí vive la invitación y su estado.
-- "Vencida" no es un estado guardado: es una PENDING con expires_at pasado y se calcula al leer. Cuando se reinvita a
-- quien tenía una invitación vencida, la fila vieja se cierra como CANCELLED con cancel_reason EXPIRED.
--
-- Una sola PENDING por empresa y técnico: pending_marker vale 1 mientras la invitación está PENDING y NULL en cualquier
-- otro estado, y la restricción única lo incluye. Como los NULL no chocan entre sí, las invitaciones ya resueltas se
-- acumulan sin límite y solo puede existir una pendiente. Se hace con una columna y no con un índice único parcial
-- (WHERE status = 'PENDING') porque H2, que usan las pruebas, no los admite y así el mismo script corre en ambos motores.
CREATE TABLE company_invitations (
    id                  UUID                     PRIMARY KEY,
    company_id          UUID                     NOT NULL,
    technician_user_id  UUID                     NOT NULL,
    status              VARCHAR(20)              NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at        TIMESTAMP WITH TIME ZONE,
    cancel_reason       VARCHAR(30),
    pending_marker      SMALLINT,
    CONSTRAINT fk_company_invitations_company FOREIGN KEY (company_id)
        REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_invitations_technician FOREIGN KEY (technician_user_id)
        REFERENCES technicians (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_company_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_company_invitations_cancel_reason CHECK (cancel_reason IS NULL
        OR cancel_reason IN ('BY_COMPANY', 'JOINED_OTHER_COMPANY', 'EXPIRED')),
    -- La razón solo existe en las canceladas, y la marca solo en las pendientes.
    CONSTRAINT ck_company_invitations_cancel_consistency CHECK (
        (status = 'CANCELLED' AND cancel_reason IS NOT NULL) OR (status <> 'CANCELLED' AND cancel_reason IS NULL)),
    CONSTRAINT ck_company_invitations_pending_marker CHECK (
        (status = 'PENDING' AND pending_marker = 1) OR (status <> 'PENDING' AND pending_marker IS NULL)),
    CONSTRAINT ck_company_invitations_expiry CHECK (expires_at > created_at),
    CONSTRAINT uk_company_invitations_pending UNIQUE (company_id, technician_user_id, pending_marker)
);

CREATE INDEX idx_company_invitations_company ON company_invitations (company_id, created_at DESC);
CREATE INDEX idx_company_invitations_technician ON company_invitations (technician_user_id);
