# Project: Payment Gateway Service

Multi-payment gateway integration microservice. OneKhusa is the first provider. The core must never depend on a specific gateway — gateway-specific logic belongs entirely inside the corresponding provider module under `gateways/`.

## Tech Stack

- **Language:** Kotlin 2.3.21
- **Framework:** Spring Boot 4.1.1
- **Java:** 21 (toolchain)
- **Build:** Gradle 9.7.1 (Kotlin DSL)
- **Database:** PostgreSQL (production), H2 (dev/test)
- **ORM:** Spring Data JPA + Hibernate
- **Migrations:** Flyway
- **HTTP Client:** Spring RestClient (current), WebClient (planned for async)
- **Security:** Spring Security + OAuth2 Client
- **Caching:** Redis (planned)
- **Messaging:** RabbitMQ (planned, deferred to Phase 3)
- **Testing:** JUnit 5 + Spring Boot Test + WireMock (planned)
- **Serialization:** Jackson with kotlin-module

## Commands

- Build: `./gradlew build`
- Test: `./gradlew test`
- Type check: `./gradlew compileKotlin`
- Run: `./gradlew bootRun`
- Clean: `./gradlew clean`
- Flyway migrate: `./gradlew flywayMigrate`

## Package Structure

Base package: `com.paymentgateway.PaymentGateway`

```
src/main/kotlin/com/paymentgateway/PaymentGateway/
├── core/
│   ├── domain/          # PaymentTransaction, PaymentStatus, GatewayType
│   ├── gateway/         # PaymentGateway interface, resolver, DTOs
│   └── exceptions/      # GatewayNotSupportedException, etc.
├── gateways/
│   └── onekhusa/        # OneKhusa adapter (auth, client, webhooks, DTOs)
├── api/                 # Controllers (PaymentController, WebhookController)
├── transactions/        # TransactionService, repository, entity
├── idempotency/         # IdempotencyService
└── config/              # Application configuration classes
```

## Code Conventions

- **Data classes** for DTOs, requests, responses — no manual equals/hashCode/toString
- **Kotlin coroutines** (`suspend fun`) for all service and client methods that do I/O
- **Spring constructor injection** — no `@Autowired` on fields
- **Named exports** — avoid default imports where possible
- **`allOpen`** is configured for `@Entity`, `@MappedSuperclass`, `@Embeddable` — do not add `data` to entities
- **Use `@Column` and `@Table` annotations explicitly** — do not rely on Hibernate naming strategies
- **Flyway for all schema changes** — never modify the schema manually or via Hibernate auto-DDL
- **Profile-specific config** via `application-{profile}.yml` (local, sandbox, production)

## Architecture Boundaries

1. **Core layer must never contain:**
   - OneKhusa-specific DTOs, URLs, auth logic, webhook signatures, or response codes
   - Any provider-specific code — only generic `PaymentRequest`, `PaymentResponse`, `PaymentStatus`, `GatewayType`

2. **Gateway adapter must:**
   - Implement the `PaymentGateway` interface
   - Live entirely under `gateways/{provider-name}/`
   - Map provider-specific formats to/from the core DTOs
   - Handle its own authentication

3. **Internal consumers communicate via:**
   - `PaymentRequest` → `PaymentResponse` (provider-neutral)
   - Never call OneKhusa APIs directly from application code outside the gateway module

## Security Boundaries

- **Never** commit `.env` files, API keys, secrets, or credentials
- **Never** log API secrets, tokens, or webhook signatures
- **Always** use environment variables for sensitive configuration
- **Always** verify webhook signatures before processing events
- **Always** use timing-safe comparison for signature checks
- Use correlation IDs for all requests

## Testing

- Unit tests for resolver, state machine, idempotency, mappers
- Integration tests against WireMock stubs (not live OneKhusa sandbox during development)
- Test webhook duplicate detection and signature verification
- Test idempotency key reuse returns same result
- Test invalid gateway type throws `GatewayNotSupportedException`
- Sandbox end-to-end tests are manual/CI-only, not run in unit test suite

## Known Gotchas

- The package is `com.paymentgateway.PaymentGateway` (note the nested `PaymentGateway`) — use this exact package in all new files
- H2 is the runtime DB for dev/test; PostgreSQL for production — SQL must be compatible with both where possible
- Spring Boot 4.1.1 uses Jakarta namespace (jakarta.persistence, not javax.persistence)
- Lombok is present as compile-only — prefer Kotlin data classes over Lombok annotations
