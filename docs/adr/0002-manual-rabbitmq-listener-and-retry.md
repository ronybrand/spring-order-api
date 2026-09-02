# ADR 0002: Manual MessageListener and retry loop instead of @RabbitListener/retry advice

## Status
Accepted

## Context
`OrderNotificationRabbitListener` consumes the order-status-changed notification queue and has to
tell apart two failure modes that need opposite handling:

- **Malformed payload** (not valid JSON, or missing a required field): never worth retrying, must
  go straight to the DLQ.
- **Transient failure** (e.g. SMTP temporarily down): worth retrying with backoff, and only routed
  to the DLQ after exhausting a bounded number of attempts.

Getting this distinction right matters: retrying a malformed message forever would waste consumer
capacity and never resolve, while not retrying a transient failure would lose notifications that
would have succeeded on the next attempt.

## Decision
Implement `MessageListener` directly (not `@RabbitListener`), so the raw message body is available
for classification before any framework-level conversion could throw first - a conversion failure
and a business-level "missing field" failure need to be distinguishable, and letting the framework
convert the payload before the code sees it would blur that line. Retry is a manual loop
(`NotificationRetryPolicy`: fixed max attempts, exponential backoff) rather than a Spring AMQP
retry advice/interceptor.

## Alternatives considered
- **`@RabbitListener` + `RetryInterceptorBuilder`/Spring Retry**: the framework-idiomatic choice,
  and the one most Spring developers would reach for first. Rejected here because it adds a
  dependency on a specific interceptor/retry-library integration and gives less direct control
  over exactly where the malformed-vs-transient distinction is made - the interceptor retries (or
  doesn't) based on exception type, which pushes the classification logic into how exceptions are
  thrown/mapped rather than keeping it explicit in one place.

## Consequences
- More code to own than the framework-provided path, but simpler to reason about and test in
  isolation: the existing unit tests call `.onMessage()` directly with hand-built `Message`
  instances, no broker or interceptor configuration involved.
- This is a deliberate choice, not a gap to "fix" later by migrating to `@RabbitListener` - the
  explicit version is kept because it's the more informative one to point to when discussing how
  the retry/DLQ contract actually works, not because the idiomatic alternative wasn't considered.
