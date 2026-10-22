ALTER TABLE technicians ADD COLUMN coverage_city VARCHAR(100);
ALTER TABLE technicians ADD COLUMN coverage_latitude DOUBLE PRECISION;
ALTER TABLE technicians ADD COLUMN coverage_longitude DOUBLE PRECISION;
ALTER TABLE technicians ADD COLUMN coverage_radius_km SMALLINT;

ALTER TABLE technicians ADD CONSTRAINT ck_technicians_coverage_circle CHECK (
    (coverage_latitude IS NULL AND coverage_longitude IS NULL AND coverage_radius_km IS NULL)
    OR (coverage_latitude IS NOT NULL AND coverage_longitude IS NOT NULL AND coverage_radius_km IS NOT NULL));
ALTER TABLE technicians ADD CONSTRAINT ck_technicians_coverage_city
    CHECK (coverage_city IS NOT NULL OR coverage_latitude IS NULL);
ALTER TABLE technicians ADD CONSTRAINT ck_technicians_coverage_latitude
    CHECK (coverage_latitude BETWEEN -90 AND 90);
ALTER TABLE technicians ADD CONSTRAINT ck_technicians_coverage_longitude
    CHECK (coverage_longitude BETWEEN -180 AND 180);
ALTER TABLE technicians ADD CONSTRAINT ck_technicians_coverage_radius
    CHECK (coverage_radius_km BETWEEN 1 AND 100);

CREATE TABLE technician_availability (
    id             UUID         PRIMARY KEY,
    technician_id  UUID         NOT NULL,
    day_of_week    VARCHAR(10)  NOT NULL,
    start_time     TIME         NOT NULL,
    end_time       TIME         NOT NULL,
    CONSTRAINT uk_technician_availability UNIQUE (technician_id, day_of_week, start_time, end_time),
    CONSTRAINT ck_technician_availability_day CHECK (day_of_week IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT ck_technician_availability_range CHECK (start_time < end_time),
    CONSTRAINT fk_technician_availability_technician FOREIGN KEY (technician_id)
        REFERENCES technicians (id) ON DELETE CASCADE
);
