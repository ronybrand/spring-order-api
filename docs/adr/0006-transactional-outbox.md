# ADR 0006: Transactional outbox instead of publishing from Spring application events

## Status
Accepted

## Context
`OrderService` used to publish domain changes (create, delete, addItem, updateItemQuantity,
removeItem, confirm, cancel) via Spring's `ApplicationEventPublisher`, consumed by
`OrderChangedEventListener` (read-model projection), `OrderDeletedEventListener` and
`OrderStatusEventListener` (customer notification), which then sent the message to RabbitMQ.

Spring application events fire synchronously in the same thread as the triggering call, but they
are not part of the database transaction: nothing rolls a published event back if the surrounding
`@Transactional` method fails after the event was raised, and nothing stops an event from being
lost if the process crashes after the database commit but before the listener finishes sending to
RabbitMQ. Both failure modes are silent - there is no dead-letter, no retry, no record that a
projection or notification never went out. The database write (the source of truth) and the
message send (a side effect that must eventually match it) had no shared atomicity guarantee.

## Decision
Adopt the transactional outbox pattern: every state change that must produce a message now writes
an `OutboxEvent` row to the `outbox_events` table in the *same* database transaction as the
domain change, via `OutboxService.enqueue(...)` called from `OrderService`. A separate
`OutboxPublisher` (see its Javadoc) polls that table on a fixed schedule, claims a batch with
`SELECT ... FOR UPDATE SKIP LOCKED` (`OutboxEventRepository.findClaimable`), sends each event to
RabbitMQ, and only marks it `PUBLISHED` after the broker send succeeds. A failed send is retried
with capped exponential backoff (`OutboxService.markFailed`); a claimed-but-never-finished event
(a publisher crash mid-batch) is reclaimed by another instance once its processing lease expires.

This replaces `ApplicationEventPublisher` and the three listener classes entirely - `OrderService`
no longer raises events for other components to react to; it writes outbox rows directly.

## Alternatives considered
- **Keep application events, add a retry/DLQ around the listeners**: does not solve the core
  problem - the event is still raised outside the database transaction, so a rollback after
  publishing or a crash before the listener runs still loses the message silently.
- **Change Data Capture (e.g. Debezium reading the `orders` table's WAL)**: gives the same
  atomicity guarantee without an explicit outbox table, but requires operating a CDC connector and
  Kafka Connect (or equivalent) infrastructure this project doesn't otherwise need, for a single
  service with a modest event volume.
- **Two-phase commit between Postgres and RabbitMQ (XA transactions)**: technically available via
  JTA, but XA is notoriously fragile in practice (broker and driver support, performance, recovery
  complexity) and is generally considered an anti-pattern relative to the outbox table approach.

## Consequences
- Message delivery is now at-least-once, not exactly-once: a crash between the successful RabbitMQ
  send and `markPublished` committing would cause the same event to be reclaimed and resent.
  Consumers must already tolerate duplicate messages (idempotent projection/notification handling),
  which is a weaker but far safer guarantee than the silent message loss this ADR replaces.
- The `SKIP LOCKED` hint (`jakarta.persistence.lock.timeout` = `-2`, Hibernate's non-portable
  signal for it, since there is no portable JPA API) is required for correctness under multiple
  `OutboxPublisher` instances polling concurrently - without it, concurrent pollers would serialize
  on `findClaimable` via row locks instead of each claiming a disjoint batch.
- Publishing is no longer synchronous with the request: an event sits `PENDING` until the next poll
  (`app.outbox.poll-delay-ms`, default 1s) picks it up, trading a small, bounded delivery latency
  for the durability guarantee.
- Adds one more piece of infrastructure to reason about (the `outbox_events` table and its
  cleanup/retention, not yet addressed) and a scheduled job's failure mode (a stuck publisher
  instance, a growing backlog) that needs monitoring - covered by
  `MessagingMetrics.registerOutboxBacklogGauge` and the `recordOutboxPublished` /
  `recordOutboxPublishFailure` / `recordOutboxPermanentlyFailed` counters.
