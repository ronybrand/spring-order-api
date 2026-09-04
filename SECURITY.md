# Security Policy

## Supported versions

This is a single-branch portfolio project (no maintained release lines) - security fixes land on
`main` only. There is no LTS/backport policy.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for a suspected vulnerability. Instead, use
[GitHub's private vulnerability reporting](../../security/advisories/new) for this repository
(Security tab → "Report a vulnerability"), or contact the maintainer directly via the email on
the [GitHub profile](https://github.com/ronybrand).

Include, where applicable:

- A description of the vulnerability and its potential impact.
- Steps to reproduce (a minimal request/payload is ideal).
- The affected endpoint(s) or component(s).

This project is maintained on a best-effort basis (no SLA), but reports are taken seriously and
triaged as soon as possible.

## Scope

In scope: the application code in this repository (`src/main`), its Spring Security
configuration, and the Dockerfile/CI workflows that build and ship it.

Out of scope: the third-party services this project integrates with when self-hosted for local
development (Keycloak, PostgreSQL, MongoDB, Redis, RabbitMQ, MailHog) - report issues in those
projects upstream. Dependency vulnerabilities are also tracked automatically via Dependabot and
CodeQL (see badges in [README.md](./README.md)) rather than manual reports.

## What this project already does

- Automated dependency updates via Dependabot, auto-merged after CI passes (minor/patch only -
  see `.github/workflows/dependabot-auto-merge.yml`).
- Static analysis on every push/PR via [CodeQL](./.github/workflows/codeql.yml) and PMD (hard-fail
  on violation, see `pom.xml`).
- No secrets committed to the repository - configuration is via environment variables
  (`application.yml` defaults are for local development only, backed by `docker-compose.yml`); see
  [README.md § Configuration & deployment notes](./README.md#configuration--deployment-notes) for
  how this maps to a real deployment.
- PII (`taxId`, `passportNumber`, etc.) is masked in logs and `toString()` output - see
  `commons/security/SensitiveDataMasker`.
