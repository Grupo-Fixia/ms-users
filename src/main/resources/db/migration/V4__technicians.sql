CREATE TABLE technicians (
    id                   UUID                     PRIMARY KEY,
    user_id              UUID                     NOT NULL,
    verification_status  VARCHAR(20)              NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_technicians_user UNIQUE (user_id),
    CONSTRAINT fk_technicians_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
