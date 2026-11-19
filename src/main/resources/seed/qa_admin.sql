-- Cuenta de administrador de Fixia para QA (GC-503). Mismas reglas que seed/qa_empresas.sql: no es una migración, solo la
-- carga QaSeedLoader con el perfil `seed-qa` y es idempotente (se inserta solo si su id, su correo o su documento todavía
-- no existen).
--
-- El rol ADMIN no se puede obtener por ningún registro público: esta es la única forma de tener uno en QA. Su contraseña
-- es la de QA documentada en el README; por eso este script nunca debe cargarse fuera de QA o DEV.
--
--   admin@fixia-qa.test -> ADMIN (ROLE_ADMIN en el token): reembolsos de ms-payments y consultas administrativas

INSERT INTO users (id, email, password_hash, role, status, first_name, last_name, document_type,
                   document_number, phone, consent_policy_version, consent_accepted_at, created_at)
SELECT CAST('ad000000-0000-4000-8000-000000000001' AS UUID), 'admin@fixia-qa.test', '$2a$12$oh3moHHwe0SQECCNWLiH9ewo4XH/6B.ftCjyPJhzGK7CZ5YK2OmuO', 'ADMIN', 'ACTIVE', 'Administración', 'Fixia', 'CC',
       '9000000001', '+573000000001', '1.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = CAST('ad000000-0000-4000-8000-000000000001' AS UUID) OR email = 'admin@fixia-qa.test'
                  OR (document_type = 'CC' AND document_number = '9000000001'));
