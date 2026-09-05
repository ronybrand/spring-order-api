# Tech debt 0001: migrate UUID columns from CHAR(36) to native `uuid`

## Status
Proposed - not scheduled, no ADR yet (this is a plan to evaluate the trade-off, not a decision).

## Context
Every UUID-typed column in this schema (`customer.id`, `order.id`, `item.id`,
`outbox_events.id`/`aggregate_id`, etc.) is stored as `CHAR(36)`, matching
`hibernate.type.preferred_uuid_jdbc_type: CHAR` in `application.yml`. This was reaffirmed for
`outbox_events` in [ADR 0006](../adr/0006-transactional-outbox.md)'s implementation - the table
was briefly created with native `UUID` columns, which broke immediately
(`operator does not exist: uuid = character varying`) precisely because the rest of the schema
uses `CHAR(36)` and Hibernate binds `UUID` parameters as strings under this setting.

Postgres's native `uuid` type would be strictly better on its own merits: 16 bytes on disk instead
of 36, binary comparisons instead of string comparisons (smaller/faster indexes), and format
validation at insert time (a `CHAR(36)` column silently accepts any 36-character string, not
necessarily a valid UUID). This document exists because that gain is real but the migration to get
it is not a one-line fix - it's a cross-cutting schema and configuration change that deserves its
own scoped plan, not something to bundle into an unrelated feature PR (this note was prompted by
the transactional outbox PR, where it was correctly kept out of scope).

## Why this wasn't done as part of the outbox PR
1. **Blast radius.** Changing `preferred_uuid_jdbc_type` is a single global Hibernate setting -
   flipping it changes how *every* entity's UUID parameters bind, not just one table's.
2. **Data migration risk.** Existing `customer`/`order`/`item` tables already have data. Changing
   their column type requires `ALTER TABLE ... ALTER COLUMN id TYPE uuid USING id::uuid` (and the
   same for every foreign key column referencing them), which needs a maintenance window and a
   rollback plan on a database with live data - not appropriate to slip in alongside an unrelated
   feature.
3. **Unknown blast radius on native queries.** Any native SQL (if present) or code treating a UUID
   column as a raw string at the JDBC level would need auditing before the type actually changes
   underneath it.

## Proposed plan (when this is picked up)
1. **ADR first.** Write an ADR recording the decision to switch, the alternatives (stay on
   `CHAR(36)`, switch only for new tables going forward), and the accepted downtime/risk.
2. **Inventory every UUID column** across all changelogs (`customer`, `order`, `item`,
   `outbox_events`, and any read-model/Mongo-adjacent tables that store an order/customer id as a
   string) and every foreign key pointing at one.
3. **Liquibase changesets**, one per table, each independently reversible:
   ```sql
   ALTER TABLE <table> ALTER COLUMN <column> TYPE uuid USING <column>::uuid;
   ```
   Foreign key columns must be migrated in the same changeset as (or immediately after) the column
   they reference, in dependency order, to avoid a window where types mismatch across a join.
4. **Flip `hibernate.type.preferred_uuid_jdbc_type`** from `CHAR` to its Hibernate default (native
   `uuid` binding) in `application.yml` and `application-test.yml`, in the same change as the last
   changeset above - the column types and the JDBC binding must move together, not in separate PRs,
   or every query breaks in between exactly like `outbox_events` did during development here.
5. **Regression pass**: full `./mvnw verify` (all `*IT`s exercise real Postgres via Testcontainers,
   so this is the actual regression net), plus explicit attention to:
   - Any native/JPQL query with a string-literal UUID comparison.
   - Pact provider/consumer tests (message payloads carry UUIDs as JSON strings regardless of the
     DB column type, so these should be unaffected, but confirm rather than assume).
   - Liquibase `preConditions`/rollback blocks for each changeset.
6. **Staged rollout** if this repo ever runs against a database with real production data: run the
   `ALTER TABLE` migrations during a maintenance window, verify row counts and a sample of
   converted values before/after, keep the changeset's `--rollback` tested.

## Consequences of staying on CHAR(36) in the meantime
- Slightly larger indexes and slower comparisons than native `uuid` - not measured, likely
  immaterial at this project's scale.
- One extra sentence of context every time a new table with a UUID id is added: it must use
  `CHAR(36)`, matching the existing convention, not native `uuid` - documented here and in
  ADR 0006 so this doesn't get rediscovered the hard way (a broken query) a second time.
