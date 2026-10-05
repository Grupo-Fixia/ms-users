CREATE TABLE users (
    id                      UUID                     PRIMARY KEY,
    email                   VARCHAR(254)             NOT NULL,
    password_hash           VARCHAR(100)             NOT NULL,
    role                    VARCHAR(20)              NOT NULL,
    status                  VARCHAR(20)              NOT NULL,
    first_name              VARCHAR(100)             NOT NULL,
    last_name               VARCHAR(100)             NOT NULL,
    document_type           VARCHAR(20)              NOT NULL,
    document_number         VARCHAR(30)              NOT NULL,
    phone                   VARCHAR(20)              NOT NULL,
    consent_policy_version  VARCHAR(20)              NOT NULL,
    consent_accepted_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_document UNIQUE (document_type, document_number)
);
