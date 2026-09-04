# Domain: Order Management

Specification of the domain implemented in this repository: business rules, entities, events and
error contract, as the source of truth independent of implementation detail.

## 1. Overview

Simple order management domain with two aggregates:

```
Customer (1) ──< Order (1) ──< Item
```

- **Customer**: customer registration data.
- **Order** (aggregate root): a customer's order, with a list of items and a calculated total.
- **Item**: order line (free-text description, no product catalog).

Out of scope: payment, inventory, product catalog, shipping/freight.

## 2. Entities

### Customer

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | generated |
| `name` | string | required |
| `taxId` | string | required, **unique**, 5–20 chars, pattern `^[A-Za-z0-9./-]{5,20}$` |
| `passportNumber` | string | optional, **unique** when present, ICAO pattern `^[A-Z0-9]{6,9}$` |
| `email` | string | required, pattern `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` |
| `marketingOptIn` | boolean | optional, default `false` — marketing communication consent, unrelated to `deletedAt`/soft-delete |
| `createdAt`, `updatedAt` | datetime | audit |
| `createdBy`, `updatedBy` | string | audit (user or "system") |
| `deletedAt`, `deletedBy` | datetime / string | soft-delete (null = active) |

- Identity equality by `taxId` (careful: mutable field — don't rely on it as a stable collection key after updates).
- `taxId` and `passportNumber` are considered sensitive data (PII) — must be masked in logs/toString.

### Order (aggregate root)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | generated |
| `customer` | reference to Customer | required |
| `items` | list of Item | composition — lifecycle tied to the Order |
| `total` | decimal | **derived**, recalculated on every item mutation |
| `status` | enum `OrderStatus` | default `OPEN` |
| `version` | integer/long | optimistic concurrency control |
| `createdAt`, `updatedAt`, `createdBy`, `updatedBy` | — | audit |
| `deletedAt`, `deletedBy` | — | soft-delete |

- Identity equality by `id`.
- **Optimistic concurrency**: every write that changes `status` or `items` must check/increment `version`; a concurrent conflict must be reported as a conflict error (HTTP 409 equivalent).

### Item (child of Order)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | generated |
| `order` | reference to the parent Order | required |
| `description` | string | required, non-blank, max 255 chars |
| `unitPrice` | decimal | required, positive, max 2 decimal places |
| `quantity` | integer | required, positive |

- Identity equality by `id` (not by description — descriptions can repeat within the same order).
- Item subtotal = `unitPrice * quantity` (calculated, not persisted).

## 3. OrderStatus enum

```
OPEN → CONFIRMED → CANCELED
OPEN → CANCELED
```

- `OPEN`: initial state. Items can be added/changed/removed. Can transition to `CONFIRMED` or `CANCELED`.
- `CONFIRMED`: items frozen (not editable). Can only transition to `CANCELED`.
- `CANCELED`: terminal state. No further transitions allowed.

## 4. Business rules / invariants

1. **Total calculation**: `order.total = Σ (item.unitPrice × item.quantity)` over all current items. Must be recalculated after any item creation/update/removal.
2. **Items editable only while order is `OPEN`**: attempting to add, change the quantity of, or remove an item on a `CONFIRMED`/`CANCELED` order is a validation error.
3. **Confirm order** (`OPEN → CONFIRMED`):
   - Fails if the current status is not `OPEN` (invalid transition).
   - Fails if the order has no items (empty order).
4. **Cancel order** (`OPEN|CONFIRMED → CANCELED`):
   - Fails if the current status is already `CANCELED`.
5. **Create order**: requires an existing `customerId`; builds the items from the request; calculates the initial total.
6. **Item limit**: maximum of 200 items per order on creation.
7. **Customer uniqueness**: `taxId` unique; `passportNumber` unique when provided (blank/absent doesn't count in the check).
8. **Customer deletion blocked**: a customer with any non-deleted order cannot be deleted (soft-deleted orders don't count).
9. **Delete is always soft-delete**: on both aggregates — never a physical removal; records with `deletedAt` set are excluded from every default query.
10. **Optimistic concurrency on Order**: conflicting concurrent mutations must fail with a conflict error, not silently overwrite each other.

## 5. Domain events

### OrderStatusChangedEvent

Fired at the end of `confirm()` and `cancel()` (only if the customer has a non-empty email).

| Field | Type |
|---|---|
| `orderId` | UUID |
| `customerEmail` | string |
| `customerName` | string |
| `oldStatus` | OrderStatus |
| `newStatus` | OrderStatus |
| `totalAmount` | decimal |
| `changedAt` | datetime |

Reference flow (original implementation): event published after transaction commit → async
messaging → email service notifies the customer of the status change. On another stack, this
could be an in-memory event, an outbox, a queue, or even a simplified synchronous call — what
matters is preserving the payload and the trigger (only on confirm/cancel, only when an email is
present).

### Async notification contract (broker + retry + DLQ)

If the implementation uses a message broker (RabbitMQ or equivalent) to decouple sending the
email from the request/response cycle, the behavior contract — not the specific technology — is
this:

1. **A malformed/invalid payload is never retried.** If the message isn't valid JSON, or is valid JSON but missing a required event field, it must go straight to the dead-letter queue (DLQ) on the first attempt. Retrying a permanently broken payload only wastes the backoff window — it will fail the same way every time.
2. **A transient failure (e.g. SMTP down) is retried with a limit.** A fixed number of attempts (reference: 3 retries after the 1st attempt, 4 attempts total) with exponential backoff (reference: 1s, 2s, 4s) before also landing in the DLQ — never unconditional/infinite retry.
3. **The DLQ is a real, inspectable queue** (e.g. RabbitMQ Management UI), not a log or silent drop — allows manual reprocessing after the root cause is fixed.
4. **Classification is by failure type, not by message content**: the same "malformed payload" exception always skips retry; any other exception (a side effect of actually sending the email) always follows the bounded retry policy.

The integration test profile starts a real RabbitMQ container (and Redis container) so the
application context and infrastructure health are exercised against the actual dependencies.
`OrderNotificationRabbitMqIT` publishes a malformed notification through the real exchange and
asserts that the consumer routes it to the real DLQ. It also publishes a valid notification while
SMTP is unavailable, then verifies the configured three retries, retry/DLQ metrics and final DLQ
delivery. Listener classification remains covered by focused tests, while Pact tests validate
message shape without a broker. A successful delivery through Mailpit is still outside the current
suite.

## 6. Domain errors (reference catalog)

Categories and codes, mappable to exceptions/HTTP status in any stack:

**Validation (client error / 400)**
- `VALIDATION_MISSING_FIELD` — required field missing.
- `VALIDATION_INVALID_CUSTOMER_ID` — non-existent customerId when creating an order.
- `VALIDATION_ORDER_NOT_EDITABLE` — attempt to edit items on a non-`OPEN` order.
- `VALIDATION_ORDER_EMPTY` — attempt to confirm an order with no items.
- `VALIDATION_ORDER_INVALID_STATUS_TRANSITION` — disallowed status transition.
- `VALIDATION_INVALID_FILTER_VALUE` / `VALIDATION_INVALID_SORT_FIELD` — invalid search parameters.
- `VALIDATION_CONSTRAINT_VIOLATION` — generic field validation violation.
- `VALIDATION_CUSTOMER_HAS_ORDERS` — deleting a customer with associated orders.

**Not found (404)**
- `RESOURCE_NOT_FOUND_CUSTOMER`
- `RESOURCE_NOT_FOUND_ORDER`
- `RESOURCE_NOT_FOUND_ITEM`
- `RESOURCE_NOT_FOUND_ORDER_VIEW` — no read-model projection exists yet, including eventual-consistency lag.

**Conflict (409)**
- `VALIDATION_CUSTOMER_TAXID_EXISTS` — duplicate taxId.
- `VALIDATION_CUSTOMER_PASSPORT_EXISTS` — duplicate passportNumber.
- `CONFLICT_CONCURRENT_MODIFICATION` — optimistic concurrency conflict.
- `CONFLICT_DATA_INTEGRITY_VIOLATION` — storage integrity violation.

**Other**
- `AUTHORIZATION_ACCESS_DENIED` — no permission for the operation.
- `INTERNAL_ERROR` — unexpected error.

## 7. Use cases (application layer)

### Order
- `create(customerId, items[])` → creates an `OPEN` order with the calculated total.
- `findById(orderId)`
- `delete(orderId)` → soft-delete.
- `addItem(orderId, item)` → requires `OPEN`; recalculates the total.
- `updateItemQuantity(orderId, itemId, quantity)` → requires `OPEN`; recalculates the total.
- `removeItem(orderId, itemId)` → requires `OPEN`; recalculates the total.
- `confirm(orderId)` → `OPEN → CONFIRMED`; requires non-empty items; publishes `OrderStatusChangedEvent`.
- `cancel(orderId)` → `OPEN|CONFIRMED → CANCELED`; publishes `OrderStatusChangedEvent`.
- `search(filters, sorting, pagination)`

### Customer
- `create(data)` → validates taxId/passportNumber uniqueness.
- `update(id, data)` → validates uniqueness excluding the record itself.
- `updateMarketingOptIn(id, value)` → updates only the `marketingOptIn` flag, without revalidating taxId/passportNumber uniqueness.
- `delete(id)` → blocked if there are associated orders; soft-delete.
- `findById(id)`
- `search(filters, sorting, pagination)`

### Order read-model
- `findView(orderId)` → reads the eventually consistent MongoDB projection; may return
   `RESOURCE_NOT_FOUND_ORDER_VIEW` immediately after a write or for an unknown order.
- Projection deletion writes a tombstone (`deletedAt`) instead of physically removing the MongoDB
   document, preventing a delayed upsert from resurrecting a deleted view.

## 8. Reference endpoints (original implementation's HTTP contract)

Reference only for the contract — replicating the same transport technology in future
implementations is not required.

Every endpoint below requires a valid JWT. A missing/invalid token returns `401`; a valid token
without the required role returns `403` with `AUTHORIZATION-01`. Error responses use the global
shape (`message`, stable `code`, optional `params` and `requestId`) and the codes listed in §6.

### `/orders` (requires the `USER` role)

| Method | Path | Role | Success | Relevant errors |
|---|---|---|---|---|
| POST | `/orders` | `USER` | `201 Created` | `400 VALIDATION-01/05/06/11` |
| GET | `/orders/search` | `USER` | `200 OK` (`Page`) | `400 VALIDATION-09/10/11` |
| GET | `/orders/{id}` | `USER` | `200 OK` | `404 RESOURCE-NOT-FOUND-02` |
| DELETE | `/orders/{id}` | `USER` | `204 No Content` | `404 RESOURCE-NOT-FOUND-02`, `409 CONFLICT-01` |
| POST | `/orders/{id}/items` | `USER` | `201 Created` | `400 VALIDATION-06/11`, `404 RESOURCE-NOT-FOUND-02` |
| PATCH | `/orders/{orderId}/items/{itemId}` | `USER` | `200 OK` | `400 VALIDATION-06/11`, `404 RESOURCE-NOT-FOUND-02/03` |
| DELETE | `/orders/{orderId}/items/{itemId}` | `USER` | `200 OK` | `400 VALIDATION-06`, `404 RESOURCE-NOT-FOUND-02/03` |
| POST | `/orders/{id}/confirm` | `USER` | `200 OK` | `400 VALIDATION-06/07/08`, `404 RESOURCE-NOT-FOUND-02`, `409 CONFLICT-01` |
| POST | `/orders/{id}/cancel` | `USER` | `200 OK` | `400 VALIDATION-08`, `404 RESOURCE-NOT-FOUND-02`, `409 CONFLICT-01` |
| GET | `/orders/{id}/view` | `USER` | `200 OK` | `404 RESOURCE-NOT-FOUND-04` |

The `/orders/{id}/view` response is eventually consistent: `404 RESOURCE-NOT-FOUND-04` can mean
the projection is still catching up after a write, not only that the order id is unknown.

### `/customers` (reads require `USER`; mutations require `ADMIN`)

| Method | Path | Role | Success | Relevant errors |
|---|---|---|---|---|
| POST | `/customers` | `ADMIN` | `201 Created` | `400 VALIDATION-01/11`, `409 VALIDATION-02/03` |
| GET | `/customers/search` | `USER` | `200 OK` (`Page`) | `400 VALIDATION-09/10/11` |
| GET | `/customers/{id}` | `USER` | `200 OK` | `404 RESOURCE-NOT-FOUND-01` |
| PUT | `/customers/{id}` | `ADMIN` | `204 No Content` | `400 VALIDATION-01/11`, `404 RESOURCE-NOT-FOUND-01`, `409 VALIDATION-02/03` |
| PATCH | `/customers/{id}/marketing-opt-in` | `ADMIN` | `204 No Content` | `400 VALIDATION-11`, `404 RESOURCE-NOT-FOUND-01` |
| DELETE | `/customers/{id}` | `ADMIN` | `204 No Content` | `400 VALIDATION-04`, `404 RESOURCE-NOT-FOUND-01` |

For search endpoints, filters use `filter[field]=value` or
`filter[field][operator]=value`, ordering uses `order=field` or `order=-field`, and `page` is
zero-based with a default `size` of 20.
