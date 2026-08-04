--liquibase formatted sql

--changeset ronybrand:3
--comment: add marketing_opt_in flag to customer (DOMAIN.md section 2)
ALTER TABLE customer ADD COLUMN marketing_opt_in BOOLEAN NOT NULL DEFAULT FALSE;
--rollback ALTER TABLE customer DROP COLUMN marketing_opt_in;
