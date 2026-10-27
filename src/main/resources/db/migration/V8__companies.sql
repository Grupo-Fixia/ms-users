CREATE TABLE companies (
    id             UUID                     PRIMARY KEY,
    admin_user_id  UUID                     NOT NULL,
    name           VARCHAR(150)             NOT NULL,
    nit            VARCHAR(30),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_companies_admin_user UNIQUE (admin_user_id),
    CONSTRAINT fk_companies_admin_user FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Un técnico pertenece a una sola empresa (uk_company_technicians_technician). Se referencia technicians.user_id para
-- que la asociación solo pueda apuntar a una cuenta que ya tiene perfil de técnico.
CREATE TABLE company_technicians (
    id                  UUID                     PRIMARY KEY,
    company_id          UUID                     NOT NULL,
    technician_user_id  UUID                     NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_company_technicians_technician UNIQUE (technician_user_id),
    CONSTRAINT fk_company_technicians_company FOREIGN KEY (company_id)
        REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_technicians_technician FOREIGN KEY (technician_user_id)
        REFERENCES technicians (user_id) ON DELETE CASCADE
);

CREATE INDEX idx_company_technicians_company ON company_technicians (company_id);
