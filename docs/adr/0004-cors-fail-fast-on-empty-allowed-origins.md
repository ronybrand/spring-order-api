# ADR 0004: Fail startup on an empty CORS allowed-origins list

## Status
Accepted

## Context
The allowed CORS origin(s) come from `app.security.cors.allowed-origins` (a CSV property, backed
by the `CORS_ALLOWED_ORIGINS` env var), never hardcoded. Outside the `dev`/`test` profiles, an
empty value is ambiguous: it could mean the operator genuinely wants no origin restricted (CORS
effectively open), or - far more likely in practice - it means the environment variable was simply
never set, an incomplete deployment configuration rather than an intentional choice.

Letting the application come up with CORS effectively open by default, silently, in that second
case is the failure mode that matters: it's a configuration mistake that would otherwise only
surface once something exploits the open CORS policy, not at deploy time when it's cheap to catch
and fix.

## Decision
Outside the `dev`/`test` profiles, an empty `allowed-origins` list throws an `IllegalStateException`
during bean creation (`corsConfigurationSource`), failing application startup outright instead of
coming up with an unintentionally permissive CORS policy.

## Alternatives considered
- **Default to a permissive policy when nothing is configured**: the opposite of secure-by-default
  - rejected because it turns a likely configuration mistake into silent, unrestricted behavior in
  production.
- **Log a warning and continue**: rejected because a startup warning is easy to miss in production
  log volume, while a failed boot is impossible to ignore - the cost of catching the mistake
  immediately (a failed deploy) is much lower than the cost of catching it later (an incident).

## Consequences
- Deploying to a non-dev/non-test environment without setting `CORS_ALLOWED_ORIGINS` fails fast,
  with a clear exception message, rather than silently exposing the API to any origin.
- `dev` and `test` profiles are deliberately exempt, since local development and the automated test
  suite have no real CORS boundary to enforce and shouldn't require that env var just to start up.
