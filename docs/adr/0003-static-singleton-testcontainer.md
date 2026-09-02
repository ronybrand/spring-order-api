# ADR 0003: Static singleton Testcontainer instead of @Testcontainers/@Container

## Status
Accepted

## Context
Every `*ControllerIT` runs against a real PostgreSQL via Testcontainers. The obvious way to wire
that up is JUnit 5's `@Testcontainers` + `@Container` pair - but that combination tears the
container down at the end of each test class. Spring Boot's test framework caches the
`ApplicationContext` across test classes when the configuration is identical (to avoid paying
Spring's startup cost per class), and that cached context holds a reference to the container's
connection details - including the port. Once the container is torn down and a new one started for
the next class, the cached context is still pointing at the now-dead port from the previous
container, and every subsequent test class using that cached context fails with connection errors
that have nothing to do with the test itself.

## Decision
The container is a `static final` field, started once in a `static {}` block, and never torn down
between test classes. Every `*ControllerIT` extends `AbstractAuthIntegrationTest`, so they all
share the same container instance and the same cached `ApplicationContext` - which stays valid for
the whole test run because the port it points at never changes.

## Alternatives considered
- **`@Testcontainers`/`@Container`**: the more common pattern, and the one most examples show -
  but it produces exactly the stale-port bug described above once Spring's context caching is
  involved, which isn't obvious until it actually happens (tests pass individually, fail when run
  together in a particular order).
- **A fresh container per test class, without relying on context caching**: correct, but expensive
  - every `*ControllerIT` would pay the cost of starting PostgreSQL from scratch, multiplying total
  suite time by the number of integration test classes.

## Consequences
- All `*ControllerIT` classes must extend `AbstractAuthIntegrationTest` (not just for the
  authenticated-request helpers, but for the shared container) - a new integration test class that
  doesn't extend it and tries to wire its own `@Container` would reintroduce the stale-port bug for
  itself and, depending on class-run order, for other tests sharing the cached context.
- The container's lifecycle is tied to the JVM running the test suite, not to any single test
  class - it stops implicitly when the test JVM exits, not via an explicit teardown call anywhere
  in the codebase.
