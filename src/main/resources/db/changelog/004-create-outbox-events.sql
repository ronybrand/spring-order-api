--liquibase formatted sql

--changeset ronybrand:4
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    locked_at TIMESTAMP,
    last_error VARCHAR(1000),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (status, available_at, created_at);
CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_id, created_at);