# Payment Gateway Service — Implementation Plan

## Problem Statement

How might we build a payment gateway service that handles OneKhusa collections today while staying architecturally ready for future providers — without over-engineering before we've validated the first integration?

## Target User

**PAYGO** — an existing application that needs to accept customer payments. The payment gateway service will be the single integration point between PAYGO and OneKhusa (and future payment providers).

## Recommended Direction

**Pragmatic Abstraction — Separate Service, Minimal Interface, OneKhusa First.**

Build the `PaymentGateway` interface and resolver from day one, but keep the abstraction minimal — just enough to support OneKhusa. Don't build a generic gateway-two scaffold. Implement OneKhusa as the first real adapter. Skip RabbitMQ for MVP. Use WireMock for development testing. Deploy as a separate Spring Boot service.

The key insight: the abstraction is cheap to build (one interface, one resolver) and expensive to retrofit (every PAYGO call site changes). Build the boundary now, keep it thin, and let it grow naturally as a second provider demands it.

The existing project scaffold (Spring Boot 4.1.1, Kotlin 2.3.21, Java 21, JPA + Flyway) is the starting point. The v2 plan documents are the architectural reference. This one-pager defines the implementation sequence.

## Key Assumptions to Validate

- [ ] **PAYGO's payment needs map to RequestToPay/Collections.** If PAYGO needs disbursements or batch payments immediately, the scope expands. → Test: Interview PAYGO team about their top 3 payment use cases before building.
- [ ] **OneKhusa sandbox is accessible and stable.** Sandbox APIs can be flaky or incomplete. → Test: Hit the sandbox auth endpoint in Week 1. If it's unreliable, WireMock becomes the primary dev environment longer than expected.
- [ ] **A second provider will actually come.** If OneKhusa is the only provider forever, the abstraction is wasted complexity. → Test: Ask the business — is there a concrete second provider being evaluated? If not, consider Direction C (monolith).
- [ ] **Synchronous webhook processing is sufficient for MVP.** If webhook volume is high or processing is slow, you'll need RabbitMQ sooner. → Test: Measure webhook processing time in sandbox. If <100ms, sync is fine.
- [ ] **PostgreSQL + Redis + Docker are available and operational.** Full stack was reported ready, but verify Redis is actually running and accessible. → Test: Connection test in Week 1.

## MVP Scope

### In Scope (The Minimum That Ships)

**Core Abstraction Layer:**
- `PaymentGateway` interface with `initiatePayment()`, `getPaymentStatus()`, `processWebhook()`
- `PaymentGatewayResolver` — selects the correct provider
- Unified `PaymentRequest` / `PaymentResponse` DTOs (provider-neutral)
- `GatewayType` enum

**OneKhusa Adapter:**
- OAuth 2.0 token service with Redis caching
- Request To Pay integration
- Transaction status mapping
- Webhook signature verification (HMAC-SHA512)
- Webhook event mapping to generic `PaymentEvent`

**Transaction Management:**
- `payment_transactions` table (generic fields + JSONB metadata)
- Idempotency key store
- Webhook events table with duplicate protection
- Transaction state machine: CREATED → INITIATED → PENDING → PAID/FAILED/EXPIRED

**Internal API:**
- `POST /api/v1/payments` — initiate payment
- `GET /api/v1/payments/{id}` — get status
- `POST /api/v1/webhooks/onekhusa` — receive webhooks

**Development:**
- WireMock stubs for OneKhusa API (auth, RequestToPay, webhook payloads)
- Docker Compose for local dev (PostgreSQL, Redis)
- Flyway migrations
- Unit tests for resolver, state machine, idempotency
- Integration tests against WireMock

**Configuration:**
- Separate `application-{profile}.yml` for sandbox/production
- Secrets via environment variables (no hardcoded credentials)

### Out of Scope (Explicitly Deferred)

- **RabbitMQ / async processing** — Add when webhook processing exceeds 100ms or volume demands it
- **Second gateway adapter** — Build when a concrete provider is identified
- **Gateway routing / smart routing** — Not needed with one provider
- **Batch disbursements** — Future capability
- **Single disbursements** — Future capability
- **Refunds / reversals** — Future capability
- **Multi-tenant gateway configuration** — Not needed for single-consumer MVP
- **Event sourcing** — Audit trail via standard DB logs is sufficient for now
- **Monitoring dashboards** — Actuator + structured logs are enough for MVP

## Implementation Sequence

### Slice 1: Project Foundation
- Add PostgreSQL driver, Redis, WebClient dependencies to `build.gradle.kts`
- Configure `application.yml` with profiles (local, sandbox)
- Docker Compose with PostgreSQL + Redis
- Flyway migration for `payment_transactions`, `idempotency_keys`, `webhook_events`
- Basic `PaymentGateway` interface and `PaymentGatewayResolver`
- `GatewayType` enum, `PaymentRequest` / `PaymentResponse` DTOs

### Slice 2: OneKhusa Auth
- `OneKhusaProperties` — configuration class
- `OneKhusaAuthClient` — OAuth token request via WebClient
- `TokenService` — check cache, request if missing/expired
- `TokenCache` — Redis-backed token storage with TTL
- WireMock stub for OAuth endpoint
- Unit + integration tests

### Slice 3: Request To Pay
- `OneKhusaCollectionsClient` — HTTP client for OneKhusa API
- `OneKhusaCollectionMapper` — map internal DTOs to/from OneKhusa format
- `TransactionService` — create transaction, persist, call adapter, return result
- `IdempotencyService` — generate/store/check keys
- `PaymentController` — `POST /api/v1/payments`
- WireMock stub for RequestToPay endpoint
- Unit + integration tests

### Slice 4: Webhook Processing
- `PaymentWebhookController` — `POST /api/v1/webhooks/onekhusa`
- `OneKhusaWebhookVerifier` — HMAC-SHA512 signature verification
- `OneKhusaWebhookHandler` — map OneKhusa event to generic `PaymentEvent`
- Duplicate event detection (unique constraint on `transaction_reference + event_type`)
- Transaction state update on verified webhook
- WireMock-based webhook simulation tests
- Duplicate delivery tests

### Slice 5: Status Polling + Hardening
- `GET /api/v1/payments/{id}` endpoint
- Error handling (gateway errors, timeouts, invalid signatures)
- Structured logging with correlation IDs
- Actuator health checks
- Security review (no secrets in logs, HTTPS enforcement)
- Sandbox end-to-end testing

## Architecture Diagram

```
PAYGO Application
       |
       | POST /api/v1/payments
       v
Payment Gateway Service
       |
       +-- PaymentController
       |       |
       |       v
       +-- TransactionService
       |       |
       |       +-- IdempotencyService (Redis)
       |       |
       |       +-- PaymentGatewayResolver
       |               |
       |               v
       |       PaymentGateway interface
       |               |
       |               v
       |       OneKhusaPaymentGateway
       |               |
       |               +-- TokenService (Redis cache)
       |               |
       |               +-- OneKhusaCollectionsClient (WebClient)
       |                       |
       |                       v
       |                   OneKhusa API
       |
       +-- PaymentWebhookController
               |
               v
       OneKhusaWebhookVerifier
               |
               v
       OneKhusaWebhookHandler
               |
               v
       TransactionService (update state)
               |
               v
       PostgreSQL
```

## Not Doing (and Why)

- **RabbitMQ from day one** — Adds operational complexity. Webhooks are synchronous-capable. Add when volume or processing time demands it.
- **Gateway-two scaffold** — Building for a provider that doesn't exist yet. The interface is enough. When a second provider arrives, you'll know what the interface actually needs.
- **Smart routing** — One provider means no routing decisions. Add when you have 2+ providers.
- **Event sourcing** — Overkill for MVP. Standard CRUD with audit columns is sufficient. Revisit if compliance demands immutable event history.
- **Microservice observability stack** (Grafana, Prometheus, distributed tracing) — Actuator + structured logs + correlation IDs are enough for MVP. Add when you have production traffic to monitor.
- **Batch operations** — Not in OneKhusa's initial collections scope. Defer until business requires it.
- **Multi-tenancy** — Single merchant account for now. The `gateway_configurations` table is designed for it but won't be implemented yet.

## Open Questions

1. **What are PAYGO's top 3 payment use cases?** This determines whether RequestToPay covers the need or if disbursements are needed immediately.
2. **Is there a concrete second provider being evaluated?** If yes, the abstraction investment is clearly justified. If no, consider whether the interface overhead is worth it.
3. **What's the expected transaction volume?** Low volume (< 100/day) means sync processing is fine. High volume (> 1000/day) means you'll want async sooner.
4. **Does PAYGO already have a payment flow, or is this greenfield?** If greenfield, you have more freedom. If replacing an existing flow, you need to match its API contract.
5. **Production timeline?** "No hard deadline" is fine, but knowing if this is weeks or months away affects how much polish goes into the MVP.

## Document Status

**Version:** 1.0
**Date:** 3 September 2026
**Status:** Draft — awaiting user confirmation before saving
