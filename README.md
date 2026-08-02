# spring-order-api

API de gestão de pedidos (Customer / Order / Item) em Spring Boot, especificada em
[`DOMAIN.md`](./DOMAIN.md) e construída em casos de uso completos por recurso, seguindo as
convenções documentadas em `AGENTS.md` e na skill `spring-feature` (local, não versionada - ver
abaixo).

## Stack

- Java 25, Spring Boot 4.1, Maven (wrapper incluído: `./mvnw`)
- PostgreSQL + Liquibase (SQL formatado)
- Spring Security + OAuth2 Resource Server (Keycloak)
- RabbitMQ + Mailpit (a partir da notificação de mudança de status do Order)
- JUnit 5 + Mockito + AssertJ + Testcontainers

## Rodando localmente

```bash
docker compose up -d postgres keycloak keycloak-db
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

A API sobe em `http://localhost:8080/api`. Swagger UI (só no profile `dev`):
`http://localhost:8080/api/swagger-ui.html`.

### Keycloak (primeira vez)

O `docker-compose.yml` só sobe o Keycloak - realm/client/roles não são provisionados
automaticamente ainda. Para testar manualmente contra o servidor real (os testes automatizados
`*ControllerIT` **não** precisam disso, usam um par de chaves RSA local):

1. Console admin em `http://localhost:8085` (`admin`/`admin`).
2. Criar um realm `orderapi`, um client confidencial com audience `order-api`, e os papéis de
   realm `USER`/`ADMIN`.
3. Criar um usuário de teste com esses papéis e obter um token via
   `POST /realms/orderapi/protocol/openid-connect/token` (grant `password` ou `client_credentials`,
   conforme o client configurado).

## Testes

```bash
./mvnw test      # unitário (*Test, sem Docker)
./mvnw verify    # unitário + integração (*IT, Postgres real via Testcontainers) + PMD + JaCoCo
```

## Estrutura

Pacote por feature (não por camada) - `customer/`, `order/` (quando existir) contêm
entidade/controller/service/repository/DTOs/specification juntos; código cross-cutting vive em
`commons/`. Detalhes completos das convenções: skill `spring-feature`
(`.claude/skills/spring-feature/SKILL.md`, local/gitignored) e `AGENTS.md` (checklist resumido,
versionado).
