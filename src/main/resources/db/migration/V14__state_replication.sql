-- Replicación de estado entre servicios (GC-547, cero dependencias síncronas).
--
-- state_publications: lo que este servicio ya publicó de cada entidad que otros necesitan (técnicos y empresas). Un
-- proceso compara cada pocos segundos el estado actual con el hash guardado y escribe en el Outbox solo lo que cambió;
-- cada tanto lo vuelve a publicar todo para que un consumidor nuevo lo encuentre aunque Kafka ya lo haya borrado.
CREATE TABLE state_publications (
    state_type      VARCHAR(60)              NOT NULL,
    state_key       UUID                     NOT NULL,
    hash            VARCHAR(64)              NOT NULL,
    sequence        BIGINT                   NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_state_publications PRIMARY KEY (state_type, state_key)
);

-- replicated_states: copia local del estado que publican otros servicios (participación en servicios, trabajo de los
-- técnicos de empresa y pagos retenidos). Se guarda la versión de mayor secuencia de cada clave; ms-users decide con
-- ella en lugar de llamar a ms-services o ms-payments.
CREATE TABLE replicated_states (
    state_type      VARCHAR(60)              NOT NULL,
    state_key       UUID                     NOT NULL,
    sequence        BIGINT                   NOT NULL,
    removed         BOOLEAN                  NOT NULL,
    data            TEXT                     NOT NULL,
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_replicated_states PRIMARY KEY (state_type, state_key)
);
