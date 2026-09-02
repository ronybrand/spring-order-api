# ADR 0001: Pessimistic locking for Customer reads, optimistic locking for Order writes

## Status
Accepted

## Context
The domain has two different concurrency problems that look similar on the surface but aren't:

1. **Reading a Customer while creating an Order.** `OrderService.create()` needs the customer to
   still exist and not be mid-deletion for the whole duration of the operation. A plain read could
   race a concurrent `CustomerService.delete` for the same customer: the read succeeds, the delete
   commits, and the order gets created against a customer that no longer exists by the time the
   transaction finishes.
2. **Concurrent updates to the same Order.** Two HTTP clients editing the same order (e.g. both
   adding an item, or one confirming while another cancels) is a classic lost-update scenario: the
   second write silently overwrites the first if nothing detects the conflict.

Both are real races, but they don't have the same shape. The first is a short window inside a
single transaction, between reading the customer and finishing the order-creation work. The
second is between two independent HTTP requests, each in its own transaction, and can happen at
any time an order is open for edits - not just during a narrow window.

## Decision
- **Pessimistic locking for Customer reads on the order-creation path**
  (`CustomerRepository.findByIdForShare`/`findByIdForUpdate`, used from `OrderService.create()`):
  blocks behind a concurrent delete instead of racing it, then re-reads correctly once the lock is
  released. Appropriate here because the window is short and contained within one transaction, so
  the cost of blocking is bounded and predictable.
- **Optimistic locking (`@Version`) on `Order`**: a version column detects lost updates between
  concurrent HTTP requests without holding a lock across requests. Appropriate here because
  collisions are rare (most orders aren't edited by two people at once) and the order is read far
  more often than it's contested, so paying a pessimistic-lock cost on every read would be
  disproportionate to the actual conflict rate.

## Alternatives considered
- **Optimistic locking for both**: doesn't solve the Customer case - the race is a plain read
  followed by unrelated logic, not a write-write conflict a version column would catch.
- **Pessimistic locking for both**: would work for Order too, but at the cost of contention on
  every read of an entity that's read far more often than it's actually edited concurrently.
  Disproportionate to the real conflict rate on that path.

## Consequences
- Two different concurrency-control mechanisms coexist in the same domain, for two genuinely
  different problems - not an inconsistency, a deliberate match between the mechanism and the
  shape of each race.
- Adding a locking strategy to a new entity/flow means asking the same question first: is this a
  short window inside one transaction (pessimistic), or a rare conflict between independent
  requests over a long-lived resource (optimistic)?
