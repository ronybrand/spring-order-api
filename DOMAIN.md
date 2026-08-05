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

Verified end-to-end against a real broker: a malformed message lands in the DLQ on the first
attempt (no retry); a forced transient failure (SMTP unavailable) produces the configured number
of attempts with the expected backoff before landing in the DLQ; the happy path delivers the
email (Mailpit in dev).

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

**Not found (404)**
- `RESOURCE_NOT_FOUND_CUSTOMER`
- `RESOURCE_NOT_FOUND_ORDER`
- `RESOURCE_NOT_FOUND_ITEM`

**Conflict (409)**
- `VALIDATION_CUSTOMER_TAXID_EXISTS` — duplicate taxId.
- `VALIDATION_CUSTOMER_PASSPORT_EXISTS` — duplicate passportNumber.
- `VALIDATION_CUSTOMER_HAS_ORDERS` — deleting a customer with associated orders.
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

## 8. Reference endpoints (original implementation's HTTP contract)

Reference only for the contract — replicating the same transport technology in future
implementations is not required.

### `/orders` (requires an authenticated user)

| Method | Path | Description |
|---|---|---|
| POST | `/orders` | Create order |
| GET | `/orders/search` | Search orders (query params) |
| GET | `/orders/{id}` | Get order by id |
| DELETE | `/orders/{id}` | Delete (soft) order |
| POST | `/orders/{id}/items` | Add item |
| PATCH | `/orders/{orderId}/items/{itemId}` | Update item quantity |
| DELETE | `/orders/{orderId}/items/{itemId}` | Remove item |
| POST | `/orders/{id}/confirm` | Confirm order |
| POST | `/orders/{id}/cancel` | Cancel order |

### `/customers` (mutations require the admin role; reads require an authenticated user)

| Method | Path | Description |
|---|---|---|
| POST | `/customers` | Create customer (admin) |
| GET | `/customers/search` | Search customers (query params) |
| GET | `/customers/{id}` | Get customer by id |
| PUT | `/customers/{id}` | Update customer (admin) |
| PATCH | `/customers/{id}/marketing-opt-in` | Update only the marketing opt-in flag (admin) |
| DELETE | `/customers/{id}` | Delete (soft) customer (admin) |
