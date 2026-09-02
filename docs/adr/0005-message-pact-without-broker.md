# ADR 0005: Message pact (Pact JVM) for the notification queue, no Pact Broker

## Status
Accepted

## Context
The order-status-changed notification message (`OrderStatusChangedEvent` published by
`OrderStatusEventListener`, consumed by `OrderNotificationRabbitListener` over RabbitMQ) had no
formal guarantee that the message actually published matches what the consumer expects to parse -
just an implicit convention shared between two classes in the same codebase. A change to either
side (a renamed field, a different date format) could silently break the other without any test
failing, since the two sides were never exercised against each other directly.

## Decision
Consumer-driven contract testing via Pact JVM's **message pact** (not HTTP pact - the contract is
about an AMQP message body, not a request/response), with no Pact Broker: the generated pact file
stays local under `target/pacts/`, gitignored like every other build artifact.

- `OrderStatusMessagePactConsumerTest` declares, from `OrderNotificationRabbitListener`'s point of
  view, the message shape it needs, then feeds a pact-generated message straight into the real
  listener - so the pact and the listener can never drift silently.
- `OrderStatusMessagePactProviderTest` verifies that a real `OrderStatusChangedEvent`, serialized
  through the same `JacksonJsonMessageConverter` `RabbitTemplate` uses in production (not a
  hand-rolled `ObjectMapper`), satisfies that contract.
- No broker, because producer and consumer live in the same service today, in the same test suite
  - there's no second, independently-deployed consumer that would need to fetch the contract from
  somewhere else.

## Alternatives considered
- **No contract testing**: the status quo before this decision, and the problem this ADR addresses
  - a format change on either side could break the other silently.
- **CDC with a Pact Broker across two independently-deployed services**: the "textbook" setup for
  consumer-driven contracts, but there's no second service actually consuming this message today -
  a broker with a single participant on each side protects nothing that the local pact file
  doesn't already protect, at the cost of hosting/operating a broker (self-hosted or a hosted
  tier).

## Consequences
- Building the provider-side verification with the real `JacksonJsonMessageConverter` instead of a
  hand-rolled Jackson `ObjectMapper` caught a real mismatch during development: the converter
  (Jackson 3) serializes `LocalDateTime` as an ISO-8601 string with a variable-length
  fractional-second component, not the fixed, seconds-only pattern first assumed on the consumer
  side. Using anything other than the actual production converter would have tested against a
  simulation of the wire format, not the wire format itself, and wouldn't have caught this.
- If a second service ever genuinely consumes this message (or a future one), a Pact Broker becomes
  worth introducing - not before, and that would be a separate decision, not an automatic extension
  of this one.
