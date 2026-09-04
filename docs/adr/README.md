# Architecture Decision Records

Log of the non-obvious technical decisions made in this project — context, what was decided,
alternatives discarded, and consequences (including the ones left deliberately unmitigated).

- [0001 — Pessimistic locking for Customer reads, optimistic locking for Order writes](0001-pessimistic-vs-optimistic-locking.md)
- [0002 — Manual MessageListener and retry loop instead of @RabbitListener/retry advice](0002-manual-rabbitmq-listener-and-retry.md)
- [0003 — Static singleton Testcontainer instead of @Testcontainers/@Container](0003-static-singleton-testcontainer.md)
- [0004 — Fail startup on an empty CORS allowed-origins list](0004-cors-fail-fast-on-empty-allowed-origins.md)
- [0005 — Message pacts (Pact JVM) for RabbitMQ flows, no Pact Broker](0005-message-pact-without-broker.md)
- [0006 — Transactional outbox instead of publishing from Spring application events](0006-transactional-outbox.md)
