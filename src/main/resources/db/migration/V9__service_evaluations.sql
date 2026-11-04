-- Evaluaciones bidireccionales de servicios cerrados (GC-444; contrato de GC-443 en docs/contracts/evaluaciones.md).
-- ms-users es el custodio de las evaluaciones originales. service_request_id es el id de la solicitud en ms-services:
-- sin clave foránea porque vive en otra base. Una por servicio y dirección (RN-012), garantizada aquí para que dos
-- envíos simultáneos no la burlen. Inmutables: no hay columnas de edición.
CREATE TABLE service_evaluations (
    id                  UUID                     PRIMARY KEY,
    service_request_id  UUID                     NOT NULL,
    direction           VARCHAR(30)              NOT NULL,
    evaluator_id        UUID                     NOT NULL,
    evaluated_id        UUID                     NOT NULL,
    rating              SMALLINT                 NOT NULL,
    comment             VARCHAR(500),
    source              VARCHAR(30)              NOT NULL,
    service_closed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_service_evaluations_direction UNIQUE (service_request_id, direction),
    CONSTRAINT ck_service_evaluations_direction CHECK (direction IN ('CLIENT_TO_PROVIDER', 'PROVIDER_TO_CLIENT')),
    CONSTRAINT ck_service_evaluations_rating CHECK (rating BETWEEN 1 AND 5),
    -- ADMINISTRATIVE_RESOLUTION (RF-055) se agrega con otra migración cuando existan las disputas.
    CONSTRAINT ck_service_evaluations_source CHECK (source IN ('CONFIRMED_CLOSURE')),
    CONSTRAINT ck_service_evaluations_parties CHECK (evaluator_id <> evaluated_id),
    CONSTRAINT fk_service_evaluations_evaluator FOREIGN KEY (evaluator_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_service_evaluations_evaluated FOREIGN KEY (evaluated_id) REFERENCES users (id) ON DELETE CASCADE
);

-- La reputación (GC-446) agrega por evaluado y dirección; «mis evaluaciones» busca por evaluador.
CREATE INDEX idx_service_evaluations_evaluated ON service_evaluations (evaluated_id, direction);
CREATE INDEX idx_service_evaluations_evaluator ON service_evaluations (evaluator_id);

-- Outbox transaccional de ms-users (GC-444). Mismas columnas que el de ms-services (su V7), para que el relay de GC-434
-- lo lea igual. Cada fila se escribe en la misma transacción que la evaluación; el relay solo actualiza las columnas de
-- envío (status en adelante). Sin clave foránea: el hecho sobrevive aunque cambie lo que lo produjo.
CREATE TABLE outbox_events (
    event_id            UUID                     PRIMARY KEY,
    event_type          VARCHAR(60)              NOT NULL,
    schema_version      SMALLINT                 NOT NULL,
    aggregate_type      VARCHAR(40)              NOT NULL,
    aggregate_id        UUID                     NOT NULL,
    aggregate_sequence  BIGINT                   NOT NULL,
    topic               VARCHAR(120)             NOT NULL,
    message_key         VARCHAR(60)              NOT NULL,
    payload             VARCHAR(16000)           NOT NULL,
    occurred_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    status              VARCHAR(20)              NOT NULL,
    attempts            INTEGER                  NOT NULL,
    next_attempt_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error          VARCHAR(500),
    published_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_outbox_aggregate_sequence UNIQUE (aggregate_id, aggregate_sequence),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_schema_version CHECK (schema_version >= 1),
    CONSTRAINT ck_outbox_sequence CHECK (aggregate_sequence >= 1),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

-- Para que el relay encuentre lo pendiente que ya toca reintentar.
CREATE INDEX ix_outbox_pending ON outbox_events (status, next_attempt_at);
