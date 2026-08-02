--liquibase formatted sql

--changeset ronybrand:1
--comment: create the customer table (DOMAIN.md section 2)
CREATE TABLE customer (
    id CHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    tax_id VARCHAR(20) NOT NULL,
    passport_number VARCHAR(9),
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255),
    CONSTRAINT uk_customer_tax_id UNIQUE (tax_id),
    CONSTRAINT uk_customer_passport_number UNIQUE (passport_number)
);

CREATE INDEX idx_customer_deleted_at ON customer (deleted_at);
--rollback DROP TABLE customer;
