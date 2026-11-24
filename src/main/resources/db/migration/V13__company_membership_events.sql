-- Historial de vinculaciones y desvinculaciones de técnicos a empresas (GC-524, docs/contracts/empresas.md §3, RF-019).
--
-- Solo inserción: cada vez que un técnico acepta una invitación queda un JOINED (con la invitación que lo originó) y
-- cada vez que se desvincula, un LEFT. La asociación vigente sigue en company_technicians; esta tabla no se edita.
-- membership_id es el id de la fila de company_technicians que nació o se borró: une el JOINED con su LEFT y es el
-- agregado de los eventos empresa.tecnico_vinculado y empresa.tecnico_desvinculado.
CREATE TABLE company_membership_events (
    id                  UUID                     PRIMARY KEY,
    membership_id       UUID                     NOT NULL,
    company_id          UUID                     NOT NULL,
    technician_user_id  UUID                     NOT NULL,
    type                VARCHAR(10)              NOT NULL,
    invitation_id       UUID,
    occurred_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_company_membership_events_company FOREIGN KEY (company_id)
        REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_membership_events_technician FOREIGN KEY (technician_user_id)
        REFERENCES technicians (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_company_membership_events_type CHECK (type IN ('JOINED', 'LEFT')),
    -- Solo una vinculación por invitación aceptada; una desvinculación no tiene invitación.
    CONSTRAINT ck_company_membership_events_invitation CHECK (
        (type = 'JOINED' AND invitation_id IS NOT NULL) OR (type = 'LEFT' AND invitation_id IS NULL)),
    CONSTRAINT uk_company_membership_events_membership UNIQUE (membership_id, type)
);

CREATE INDEX idx_company_membership_events_company ON company_membership_events (company_id, occurred_at);
CREATE INDEX idx_company_membership_events_technician ON company_membership_events (technician_user_id, occurred_at);
