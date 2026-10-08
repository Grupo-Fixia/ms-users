ALTER TABLE technicians ADD COLUMN professional_description VARCHAR(1000);
ALTER TABLE technicians ADD COLUMN years_of_experience SMALLINT;

CREATE TABLE technician_categories (
    id             UUID         PRIMARY KEY,
    technician_id  UUID         NOT NULL,
    category       VARCHAR(50)  NOT NULL,
    CONSTRAINT uk_technician_categories UNIQUE (technician_id, category),
    CONSTRAINT fk_technician_categories_technician FOREIGN KEY (technician_id)
        REFERENCES technicians (id) ON DELETE CASCADE
);
