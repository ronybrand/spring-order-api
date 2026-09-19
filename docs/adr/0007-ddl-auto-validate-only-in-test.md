# ADR 0007: hibernate.ddl-auto=validate only in tests, never in production

## Status
Accepted

## Context
`spring.jpa.hibernate.ddl-auto` is `none` in `application.yml` (every real profile, including
production) - Liquibase owns schema changes, not Hibernate. Nothing then verified that the
Liquibase changelog and the JPA entity mappings actually agree: adding a `@Column` without a
matching changeset (or vice versa) would only surface later, as a runtime SQL error against
production data.

A comparative review against the sibling `estado` project (same portfolio, also Liquibase +
Hibernate) surfaced the same trade-off from the opposite starting point: `estado` originally had
`ddl-auto: validate` unconditionally, including production (its own ADR 0014), and moved it to
test-only after this project's approach was pointed out as the safer default (`estado`'s ADR
0020). Writing this ADR closes the gap of that same decision never having been documented here in
the first place - it existed only as a Javadoc comment on the test class.

## Decision
`SchemaValidationIT` boots the full context - same Testcontainers Postgres/Liquibase migrations
the rest of the `*IT` suite already runs against (see `AbstractAuthIntegrationTest`) - with
`ddl-auto=validate` overridden just for that test class via `@TestPropertySource`. Hibernate
validates the mapped schema against the actual database at `EntityManagerFactory` creation and
throws `SchemaManagementException` on any mismatch; a successful context load is the assertion. No
new CI job needed - it runs inside the existing `mvn verify`.

## Alternatives considered
- **`ddl-auto: validate` unconditionally, including production** (`estado`'s original choice,
  since revised): `validate` never alters schema, so it's not the well-known `update`/`create`
  anti-pattern, but it's still a known source of Hibernate false positives - naming-strategy
  mismatches, database-specific type mapping, an index Hibernate expects one way and Liquibase
  created another. Running that check in production means a false positive breaks a real
  deployment, at the worst possible time; running it only in `*IT` means the same false positive
  costs a PR comment.
- **A dedicated `application-test.yml` profile**: more explicit, but this project doesn't have one
  today - introducing a whole profile file just for one property would be more ceremony than the
  `@TestPropertySource` already on the test class that needs it.

## Consequences
- Positive: an incorrect entity mapping still breaks CI (same guarantee as before), but never a
  production boot, regardless of how the Hibernate/Liquibase disagreement is triggered.
- Neutral: zero behavior change in production outside the check itself - Hibernate was never
  altering schema (`validate` doesn't run DDL) with either setting, only conferring or not.
- Negative accepted: if Liquibase and the entities diverge in a way that only shows up under
  production-only data/encoding/volume, `SchemaValidationIT` may not catch it - the same residual
  risk `estado` accepted when adopting this same approach.
