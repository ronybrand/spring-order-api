--liquibase formatted sql

--changeset ronybrand:5
-- OutboxEventRepository#findClaimable's PENDING branch is fully covered by
-- idx_outbox_events_pending (status, available_at, created_at); its PROCESSING branch instead
-- filters on locked_at, a column that index doesn't cover, so Postgres could only use the status
-- equality there and had to scan the rest. Cheap today because PROCESSING rows are normally few
-- (claimed-but-not-yet-published events), but a growing PROCESSING backlog (e.g. a broker outage)
-- would make every poll cycle's claim query pay an unindexed filter on top of the indexed PENDING
-- scan. A partial index, scoped to PROCESSING rows only, closes that gap without the overhead of
-- indexing PENDING/PUBLISHED/FAILED rows a second time.
CREATE INDEX idx_outbox_events_processing ON outbox_events (locked_at) WHERE status = 'PROCESSING';
