-- Datos de QA para GC-466 (empresas y técnicos asociados). NO es una migración de producción: vive en db/seed y solo
-- se carga con el perfil `seed-qa` (ver application-seed-qa.yml). Es repetible e idempotente: cada fila se inserta solo
-- si su id (o, en usuarios, su correo o documento) todavía no existe, así que puede ejecutarse cuantas veces haga falta.
--
-- Cuentas (todas con la contraseña de QA documentada en el README):
--   empresa1@fixia-qa.test, empresa2@fixia-qa.test  -> empresas con 2 técnicos asociados cada una
--   empresa3@fixia-qa.test                          -> empresa sin técnicos (lista vacía)
--   tecnico1..4@fixia-qa.test                       -> técnicos asociados a empresa1 (1, 2) y empresa2 (3, 4)

-- Cuentas de administradores de empresa
INSERT INTO users (id, email, password_hash, role, status, first_name, last_name, document_type,
                   document_number, phone, consent_policy_version, consent_accepted_at, created_at)
SELECT CAST('c0a00000-0000-4000-8000-000000000001' AS UUID), 'empresa1@fixia-qa.test', '$2a$12$oh3moHHwe0SQECCNWLiH9ewo4XH/6B.ftCjyPJhzGK7CZ5YK2OmuO', 'COMPANY', 'ACTIVE', 'Laura', 'Gómez', 'CC',
       '9100000001', '+573001000001', '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = CAST('c0a00000-0000-4000-8000-000000000001' AS UUID) OR email = 'empresa1@fixia-qa.test'
                  OR (document_type = 'CC' AND document_number = '9100000001'));

INSERT INTO users (id, email, password_hash, role, status, first_name, last_name, document_type,
                   document_number, phone, consent_policy_version, consent_accepted_at, created_at)
SELECT CAST('c0a00000-0000-4000-8000-000000000002' AS UUID), 'empresa2@fixia-qa.test', '$2a$12$oh3moHHwe0SQECCNWLiH9ewo4XH/6B.ftCjyPJhzGK7CZ5YK2OmuO', 'COMPANY', 'ACTIVE', 'Andrés', 'Ríos', 'CC',
       '9100000002', '+573001000002', '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = CAST('c0a00000-0000-4000-8000-000000000002' AS UUID) OR email = 'empresa2@fixia-qa.test'
                  OR (document_type = 'CC' AND document_number = '9100000002'));

INSERT INTO users (id, email, password_hash, role, status, first_name, last_name, document_type,
                   document_number, phone, consent_policy_version, consent_accepted_at, created_at)
SELECT CAST('c0a00000-0000-4000-8000-000000000003' AS UUID), 'empresa3@fixia-qa.test', '$2a$12$oh3moHHwe0SQECCNWLiH9ewo4XH/6B.ftCjyPJhzGK7CZ5YK2OmuO', 'COMPANY', 'ACTIVE', 'Marta', 'Cruz', 'CC',
       '9100000003', '+573001000003', '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = CAST('c0a00000-0000-4000-8000-000000000003' AS UUID) OR email = 'empresa3@fixia-qa.test'
                  OR (document_type = 'CC' AND document_number = '9100000003'));

-- Cuentas de técnicos
INSERT INTO users (id, email, password_hash, role, status, first_name, last_name, document_type,
                   document_number, phone, consent_policy_version, consent_accepted_at, created_at)
SELECT CAST('7ec00000-0000-4000-8000-000000000001' AS UUID), 'tecnico1@fixia-qa.test', '$2a$12$oh3moHHwe0SQECCNWLiH9ewo4XH/6B.ftCjyPJhzGK7CZ5YK2OmuO', 'PROFESSIONAL', 'ACTIVE', 'Carlos', 'Giraldo', 'CC',
       '9200000001', '+573002000001', '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = CAST('7ec00000-0000-4000-8000-000000000001' AS UUID) OR email = 'tecnico1@fixia-qa.test'
                  OR (document_type = 'CC' AND document_number = '9200000001'));

INSERT INTO users (id, email, password_hash, role, status, first_name, last_name, document_type,
                   document_number, phone, consent_policy_version, consent_accepted_at, created_at)
SELECT CAST('7ec00000-0000-4000-8000-000000000002' AS UUID), 'tecnico2@fixia-qa.test', '$2a$12$oh3moHHwe0SQECCNWLiH9ewo4XH/6B.ftCjyPJhzGK7CZ5YK2OmuO', 'PROFESSIONAL', 'ACTIVE', 'Diana', 'Torres', 'CC',
       '9200000002', '+573002000002', '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = CAST('7ec00000-0000-4000-8000-000000000002' AS UUID) OR email = 'tecnico2@fixia-qa.test'
                  OR (document_type = 'CC' AND document_number = '9200000002'));

INSERT INTO users (id, email, password_hash, role, status, first_name, last_name, document_type,
                   document_number, phone, consent_policy_version, consent_accepted_at, created_at)
SELECT CAST('7ec00000-0000-4000-8000-000000000003' AS UUID), 'tecnico3@fixia-qa.test', '$2a$12$oh3moHHwe0SQECCNWLiH9ewo4XH/6B.ftCjyPJhzGK7CZ5YK2OmuO', 'PROFESSIONAL', 'ACTIVE', 'Julián', 'Pardo', 'CC',
       '9200000003', '+573002000003', '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = CAST('7ec00000-0000-4000-8000-000000000003' AS UUID) OR email = 'tecnico3@fixia-qa.test'
                  OR (document_type = 'CC' AND document_number = '9200000003'));

INSERT INTO users (id, email, password_hash, role, status, first_name, last_name, document_type,
                   document_number, phone, consent_policy_version, consent_accepted_at, created_at)
SELECT CAST('7ec00000-0000-4000-8000-000000000004' AS UUID), 'tecnico4@fixia-qa.test', '$2a$12$oh3moHHwe0SQECCNWLiH9ewo4XH/6B.ftCjyPJhzGK7CZ5YK2OmuO', 'PROFESSIONAL', 'ACTIVE', 'Sofía', 'Mejía', 'CC',
       '9200000004', '+573002000004', '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = CAST('7ec00000-0000-4000-8000-000000000004' AS UUID) OR email = 'tecnico4@fixia-qa.test'
                  OR (document_type = 'CC' AND document_number = '9200000004'));

-- Perfiles de técnico (verificados, con ciudad de cobertura)
INSERT INTO technicians (id, user_id, verification_status, professional_description, years_of_experience,
                         coverage_city, created_at, updated_at)
SELECT CAST('7e000000-0000-4000-8000-000000000001' AS UUID), CAST('7ec00000-0000-4000-8000-000000000001' AS UUID), 'VALID', 'Plomería residencial y reparación de fugas.', 8, 'Cali',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM users WHERE id = CAST('7ec00000-0000-4000-8000-000000000001' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM technicians WHERE user_id = CAST('7ec00000-0000-4000-8000-000000000001' AS UUID));

INSERT INTO technicians (id, user_id, verification_status, professional_description, years_of_experience,
                         coverage_city, created_at, updated_at)
SELECT CAST('7e000000-0000-4000-8000-000000000002' AS UUID), CAST('7ec00000-0000-4000-8000-000000000002' AS UUID), 'VALID', 'Instalaciones eléctricas domiciliarias.', 5, 'Cali',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM users WHERE id = CAST('7ec00000-0000-4000-8000-000000000002' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM technicians WHERE user_id = CAST('7ec00000-0000-4000-8000-000000000002' AS UUID));

INSERT INTO technicians (id, user_id, verification_status, professional_description, years_of_experience,
                         coverage_city, created_at, updated_at)
SELECT CAST('7e000000-0000-4000-8000-000000000003' AS UUID), CAST('7ec00000-0000-4000-8000-000000000003' AS UUID), 'VALID', 'Pintura de interiores y carpintería ligera.', 6, 'Palmira',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM users WHERE id = CAST('7ec00000-0000-4000-8000-000000000003' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM technicians WHERE user_id = CAST('7ec00000-0000-4000-8000-000000000003' AS UUID));

INSERT INTO technicians (id, user_id, verification_status, professional_description, years_of_experience,
                         coverage_city, created_at, updated_at)
SELECT CAST('7e000000-0000-4000-8000-000000000004' AS UUID), CAST('7ec00000-0000-4000-8000-000000000004' AS UUID), 'VALID', 'Cerrajería de emergencia y cambio de chapas.', 4, 'Palmira',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM users WHERE id = CAST('7ec00000-0000-4000-8000-000000000004' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM technicians WHERE user_id = CAST('7ec00000-0000-4000-8000-000000000004' AS UUID));

-- Especialidades
INSERT INTO technician_categories (id, technician_id, category)
SELECT CAST('ca700000-0000-4000-8000-000000000001' AS UUID), t.id, 'PLUMBING'
FROM technicians t
WHERE t.user_id = CAST('7ec00000-0000-4000-8000-000000000001' AS UUID)
  AND NOT EXISTS (SELECT 1 FROM technician_categories WHERE technician_id = t.id AND category = 'PLUMBING');

INSERT INTO technician_categories (id, technician_id, category)
SELECT CAST('ca700000-0000-4000-8000-000000000002' AS UUID), t.id, 'MAINTENANCE'
FROM technicians t
WHERE t.user_id = CAST('7ec00000-0000-4000-8000-000000000001' AS UUID)
  AND NOT EXISTS (SELECT 1 FROM technician_categories WHERE technician_id = t.id AND category = 'MAINTENANCE');

INSERT INTO technician_categories (id, technician_id, category)
SELECT CAST('ca700000-0000-4000-8000-000000000003' AS UUID), t.id, 'ELECTRICAL'
FROM technicians t
WHERE t.user_id = CAST('7ec00000-0000-4000-8000-000000000002' AS UUID)
  AND NOT EXISTS (SELECT 1 FROM technician_categories WHERE technician_id = t.id AND category = 'ELECTRICAL');

INSERT INTO technician_categories (id, technician_id, category)
SELECT CAST('ca700000-0000-4000-8000-000000000004' AS UUID), t.id, 'PAINTING'
FROM technicians t
WHERE t.user_id = CAST('7ec00000-0000-4000-8000-000000000003' AS UUID)
  AND NOT EXISTS (SELECT 1 FROM technician_categories WHERE technician_id = t.id AND category = 'PAINTING');

INSERT INTO technician_categories (id, technician_id, category)
SELECT CAST('ca700000-0000-4000-8000-000000000005' AS UUID), t.id, 'CARPENTRY'
FROM technicians t
WHERE t.user_id = CAST('7ec00000-0000-4000-8000-000000000003' AS UUID)
  AND NOT EXISTS (SELECT 1 FROM technician_categories WHERE technician_id = t.id AND category = 'CARPENTRY');

INSERT INTO technician_categories (id, technician_id, category)
SELECT CAST('ca700000-0000-4000-8000-000000000006' AS UUID), t.id, 'LOCKSMITHING'
FROM technicians t
WHERE t.user_id = CAST('7ec00000-0000-4000-8000-000000000004' AS UUID)
  AND NOT EXISTS (SELECT 1 FROM technician_categories WHERE technician_id = t.id AND category = 'LOCKSMITHING');

-- Empresas
INSERT INTO companies (id, admin_user_id, name, nit, created_at)
SELECT CAST('c0000000-0000-4000-8000-000000000001' AS UUID), CAST('c0a00000-0000-4000-8000-000000000001' AS UUID), 'Servicios Integrales Andina', '9001000011', CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM users WHERE id = CAST('c0a00000-0000-4000-8000-000000000001' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM companies WHERE admin_user_id = CAST('c0a00000-0000-4000-8000-000000000001' AS UUID));

INSERT INTO companies (id, admin_user_id, name, nit, created_at)
SELECT CAST('c0000000-0000-4000-8000-000000000002' AS UUID), CAST('c0a00000-0000-4000-8000-000000000002' AS UUID), 'Mantenimientos del Valle', '9001000022', CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM users WHERE id = CAST('c0a00000-0000-4000-8000-000000000002' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM companies WHERE admin_user_id = CAST('c0a00000-0000-4000-8000-000000000002' AS UUID));

INSERT INTO companies (id, admin_user_id, name, nit, created_at)
SELECT CAST('c0000000-0000-4000-8000-000000000003' AS UUID), CAST('c0a00000-0000-4000-8000-000000000003' AS UUID), 'Hogar Experto (sin técnicos)', '9001000033', CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM users WHERE id = CAST('c0a00000-0000-4000-8000-000000000003' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM companies WHERE admin_user_id = CAST('c0a00000-0000-4000-8000-000000000003' AS UUID));

-- Asociaciones empresa-técnico (un técnico, una sola empresa)
INSERT INTO company_technicians (id, company_id, technician_user_id, created_at)
SELECT CAST('a5500000-0000-4000-8000-000000000001' AS UUID), c.id, CAST('7ec00000-0000-4000-8000-000000000001' AS UUID), CURRENT_TIMESTAMP
FROM companies c
WHERE c.admin_user_id = CAST('c0a00000-0000-4000-8000-000000000001' AS UUID)
  AND EXISTS (SELECT 1 FROM technicians WHERE user_id = CAST('7ec00000-0000-4000-8000-000000000001' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM company_technicians WHERE technician_user_id = CAST('7ec00000-0000-4000-8000-000000000001' AS UUID));

INSERT INTO company_technicians (id, company_id, technician_user_id, created_at)
SELECT CAST('a5500000-0000-4000-8000-000000000002' AS UUID), c.id, CAST('7ec00000-0000-4000-8000-000000000002' AS UUID), CURRENT_TIMESTAMP
FROM companies c
WHERE c.admin_user_id = CAST('c0a00000-0000-4000-8000-000000000001' AS UUID)
  AND EXISTS (SELECT 1 FROM technicians WHERE user_id = CAST('7ec00000-0000-4000-8000-000000000002' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM company_technicians WHERE technician_user_id = CAST('7ec00000-0000-4000-8000-000000000002' AS UUID));

INSERT INTO company_technicians (id, company_id, technician_user_id, created_at)
SELECT CAST('a5500000-0000-4000-8000-000000000003' AS UUID), c.id, CAST('7ec00000-0000-4000-8000-000000000003' AS UUID), CURRENT_TIMESTAMP
FROM companies c
WHERE c.admin_user_id = CAST('c0a00000-0000-4000-8000-000000000002' AS UUID)
  AND EXISTS (SELECT 1 FROM technicians WHERE user_id = CAST('7ec00000-0000-4000-8000-000000000003' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM company_technicians WHERE technician_user_id = CAST('7ec00000-0000-4000-8000-000000000003' AS UUID));

INSERT INTO company_technicians (id, company_id, technician_user_id, created_at)
SELECT CAST('a5500000-0000-4000-8000-000000000004' AS UUID), c.id, CAST('7ec00000-0000-4000-8000-000000000004' AS UUID), CURRENT_TIMESTAMP
FROM companies c
WHERE c.admin_user_id = CAST('c0a00000-0000-4000-8000-000000000002' AS UUID)
  AND EXISTS (SELECT 1 FROM technicians WHERE user_id = CAST('7ec00000-0000-4000-8000-000000000004' AS UUID))
  AND NOT EXISTS (SELECT 1 FROM company_technicians WHERE technician_user_id = CAST('7ec00000-0000-4000-8000-000000000004' AS UUID));
