ALTER TABLE technician_categories ADD CONSTRAINT ck_technician_categories_category
    CHECK (category IN ('PLUMBING', 'ELECTRICAL', 'MAINTENANCE', 'LOCKSMITHING', 'PAINTING', 'CARPENTRY'));

ALTER TABLE technicians ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
