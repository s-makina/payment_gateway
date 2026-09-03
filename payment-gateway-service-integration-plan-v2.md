# Multi-Payment Gateway Integration Service Plan

## 1. Purpose

This document defines the architecture and implementation plan for a **multi-payment gateway integration microservice**.

The service will provide a single internal payment abstraction for applications while allowing multiple payment gateway providers to be integrated behind a common architecture.

**OneKhusa is not the main project.** It is the first payment gateway provider and should exist as a dedicated submodule/subfolder within the project.

```text
payment-gateway-service/
├── core/
├── gateways/
│   └── onekhusa/
├── api/
├── transactions/
├── webhooks/
└── infrastructure/
```

Future providers can be added without changing the core payment workflow:

```text
gateways/
├── onekhusa/
├── gateway-two/
├── mobile-money-provider/
└── bank-provider/
```

---

# 2. Primary Architectural Goal

Internal applications should **never depend directly on a specific payment gateway**.

Instead:

```text
Internal Application
        |
        v
Payment Gateway Service
        |
        v
Gateway Resolver
        |
        +-------------------+
        |                   |
        v                   v
     OneKhusa          Future Gateway
        |                   |
        v                   v
   OneKhusa API       Provider API
```

This approach provides:

- Gateway independence
- Easier provider replacement
- Support for multiple payment providers
- Unified transaction management
- Consistent internal APIs
- Centralized webhook processing
- Reduced vendor lock-in

---

# 3. Recommended Technology Stack

## Core

- **Language:** Kotlin
- **Framework:** Spring Boot
- **Build Tool:** Gradle
- **Database:** PostgreSQL
- **HTTP Client:** Spring WebClient
- **Database Migration:** Flyway

## Supporting Services

- **Redis:** Token caching and distributed locking
- **RabbitMQ:** Asynchronous payment and webhook processing
- **Docker:** Containerization
- **Spring Boot Actuator:** Health checks and metrics

---

# 4. Project Structure

Recommended package structure:

```text
payment-gateway-service/
│
├── core/
│   ├── domain/
│   │   ├── PaymentTransaction.kt
│   │   ├── PaymentStatus.kt
│   │   ├── PaymentMethod.kt
│   │   └── GatewayType.kt
│   │
│   ├── gateway/
│   │   ├── PaymentGateway.kt
│   │   ├── PaymentGatewayResolver.kt
│   │   ├── GatewayRequest.kt
│   │   └── GatewayResponse.kt
│   │
│   └── exceptions/
│
├── gateways/
│   │
│   ├── onekhusa/
│   │   ├── config/
│   │   ├── auth/
│   │   ├── collections/
│   │   ├── requesttopay/
│   │   ├── webhooks/
│   │   ├── client/
│   │   └── OneKhusaPaymentGateway.kt
│   │
│   ├── gateway-two/
│   │   ├── client/
│   │   ├── webhooks/
│   │   └── GatewayTwoPaymentGateway.kt
│   │
│   └── mobile-money/
│       └── ...
│
├── api/
│   ├── PaymentController.kt
│   └── PaymentWebhookController.kt
│
├── transactions/
│   ├── TransactionService.kt
│   ├── TransactionRepository.kt
│   └── TransactionEntity.kt
│
├── events/
│   ├── PaymentEventPublisher.kt
│   └── PaymentEventConsumer.kt
│
├── idempotency/
│   └── IdempotencyService.kt
│
└── infrastructure/
    ├── redis/
    ├── rabbitmq/
    └── persistence/
```

---

# 5. Core Payment Gateway Interface

Every provider should implement a common interface.

Example:

```kotlin
interface PaymentGateway {

    fun getGatewayType(): GatewayType

    suspend fun initiatePayment(
        request: PaymentRequest
    ): PaymentInitiationResult

    suspend fun getPaymentStatus(
        transactionReference: String
    ): PaymentStatusResult

    suspend fun processWebhook(
        request: GatewayWebhookRequest
    ): WebhookProcessingResult
}
```

The exact interface can expand as more payment capabilities are added.

For example:

```kotlin
interface PaymentGateway {

    suspend fun initiateCollection(request: CollectionRequest): PaymentResult

    suspend fun initiateDisbursement(request: DisbursementRequest): PaymentResult

    suspend fun getTransactionStatus(reference: String): PaymentStatusResult

    suspend fun verifyWebhook(request: GatewayWebhookRequest): Boolean

    suspend fun processWebhook(request: GatewayWebhookRequest): WebhookProcessingResult
}
```

Not every provider must support every capability. Capability checks can be introduced later.

---

# 6. Gateway Resolver

The gateway resolver selects the correct provider.

```text
Payment Request
       |
       v
Gateway Resolver
       |
       +------------------+
       |                  |
       v                  v
  ONEKHUSA           GATEWAY_TWO
       |                  |
       v                  v
 OneKhusaGateway    GatewayTwoGateway
```

Example:

```kotlin
@Service
class PaymentGatewayResolver(
    private val gateways: List<PaymentGateway>
) {

    fun resolve(gatewayType: GatewayType): PaymentGateway {
        return gateways.firstOrNull {
            it.getGatewayType() == gatewayType
        } ?: throw GatewayNotSupportedException(gatewayType)
    }
}
```

This allows new gateways to be added without modifying the main payment service.

---

# 7. Unified Internal Payment API

Applications communicate with a provider-neutral API.

## Initiate Payment

```http
POST /api/v1/payments
```

Example:

```json
{
  "gateway": "ONEKHUSA",
  "paymentType": "COLLECTION",
  "amount": 10000,
  "currency": "MWK",
  "reference": "INV-10001",
  "customer": {
    "id": "customer-123"
  }
}
```

The response should also remain provider-neutral:

```json
{
  "transactionId": "uuid",
  "gateway": "ONEKHUSA",
  "status": "PENDING",
  "reference": "INV-10001",
  "paymentInstructions": {}
}
```

---

# 8. Gateway Abstraction Rules

The core layer must not contain:

- OneKhusa-specific DTOs
- OneKhusa API URLs
- OneKhusa authentication logic
- OneKhusa webhook signatures
- OneKhusa-specific response codes

Those concerns belong only inside:

```text
gateways/onekhusa/
```

The core application should only understand:

```text
PaymentRequest
PaymentTransaction
PaymentStatus
GatewayType
PaymentGateway
WebhookEvent
```

This is essential to avoid coupling the entire project to the first provider.

---

# 9. OneKhusa Provider Module

OneKhusa should be implemented as:

```text
gateways/
└── onekhusa/
    │
    ├── config/
    │   └── OneKhusaProperties.kt
    │
    ├── auth/
    │   ├── OneKhusaAuthClient.kt
    │   ├── OneKhusaTokenService.kt
    │   └── OneKhusaTokenCache.kt
    │
    ├── client/
    │   └── OneKhusaApiClient.kt
    │
    ├── collections/
    │   ├── OneKhusaCollectionsClient.kt
    │   └── OneKhusaCollectionMapper.kt
    │
    ├── requesttopay/
    │   └── OneKhusaRequestToPayService.kt
    │
    ├── webhooks/
    │   ├── OneKhusaWebhookVerifier.kt
    │   └── OneKhusaWebhookHandler.kt
    │
    ├── dto/
    │   ├── request/
    │   └── response/
    │
    └── OneKhusaPaymentGateway.kt
```

The primary adapter implements the common interface:

```text
OneKhusaPaymentGateway
        |
        implements
        |
        v
PaymentGateway
```

---

# 10. OneKhusa API References

The OneKhusa integration module should use the official documentation below.

## Developer Portal

https://docs.onekhusa.com/

## API Introduction and Authentication

https://docs.onekhusa.com/api-reference/get-started/introduction

OneKhusa uses OAuth 2.0/OIDC authentication. The access token is valid for approximately five minutes and should be cached and refreshed when necessary.

## Collections Overview

https://docs.onekhusa.com/api-reference/collections/overview

OneKhusa Collections allow merchants to accept payments from supported financial institutions and digital channels.

## Quick Integration

https://docs.onekhusa.com/getting-started/quick-integration

The Quick Integration guide covers portal setup, credentials, Request To Pay, sandbox testing, webhooks, collections, and production deployment.

## Webhook Overview

https://docs.onekhusa.com/api-reference/webhooks/overview

OneKhusa uses an event-driven model for transaction updates.

## Recent Documentation Changes

https://docs.onekhusa.com/changelog/19august2026

The OneKhusa changelog should be monitored because the provider module may need updates when the external API changes.

---

# 11. OneKhusa Authentication Adapter

The OneKhusa module is responsible for OAuth authentication.

```text
Payment Service
       |
       v
OneKhusaPaymentGateway
       |
       v
OneKhusaTokenService
       |
       v
Redis Token Cache
       |
       +-- Valid Token --> OneKhusa API
       |
       +-- Missing Token
              |
              v
       OAuth Token Endpoint
```

Configuration should remain isolated:

```yaml
payment:
  gateways:
    onekhusa:
      enabled: true
      environment: sandbox
      api-key: ${ONEKHUSA_API_KEY}
      api-secret: ${ONEKHUSA_API_SECRET}
      organisation-id: ${ONEKHUSA_ORGANISATION_ID}
      merchant-account-number: ${ONEKHUSA_MERCHANT_ACCOUNT_NUMBER}
      webhook-secret: ${ONEKHUSA_WEBHOOK_SECRET}
```

No OneKhusa credentials should be exposed outside the provider configuration.

---

# 12. Unified Transaction Model

A transaction should have both generic and provider-specific references.

```text
PaymentTransaction
--------------------------------
id
gateway
gateway_transaction_id
merchant_reference
amount
currency
payment_type
status
idempotency_key
created_at
updated_at
completed_at
```

Example:

```text
id:
    8b4f...

gateway:
    ONEKHUSA

merchant_reference:
    INV-10001

gateway_transaction_id:
    TXN7D3P8L5Q2X

status:
    SUCCESS
```

This allows every transaction to be traced internally and externally.

---

# 13. Provider Transaction Metadata

Different providers return different fields.

Do not force all provider-specific fields into the main transaction table.

Use:

```text
payment_transaction_metadata
--------------------------------
id
transaction_id
gateway
metadata_key
metadata_value
```

Or store provider metadata as JSON:

```text
gateway_metadata JSONB
```

Example OneKhusa metadata:

```json
{
  "timedAccountNumber": "11005533",
  "connectorId": 247482,
  "sourceInstitution": "Example Bank"
}
```

This keeps the core schema independent from any provider.

---

# 14. Idempotency Architecture

The platform should have two layers of idempotency.

## Internal Idempotency

Prevents duplicate requests from internal applications.

```text
Client
  |
  | Idempotency-Key
  v
Payment Service
  |
  +-- Existing Request --> Return Previous Result
  |
  +-- New Request
          |
          v
       Gateway Adapter
```

## Provider Idempotency

The provider adapter translates or forwards the idempotency key according to the provider's API requirements.

For OneKhusa, mutation operations require an idempotency key.

Reference:

https://docs.onekhusa.com/getting-started/idempotency

---

# 15. Webhook Architecture

Each gateway should have its own webhook route.

```text
/api/v1/webhooks/onekhusa
/api/v1/webhooks/gateway-two
/api/v1/webhooks/mobile-money
```

All routes eventually produce a common internal event:

```text
Gateway Webhook
       |
       v
Provider-Specific Handler
       |
       v
Verify Provider Signature
       |
       v
Convert to PaymentEvent
       |
       v
Persist Event
       |
       v
RabbitMQ
       |
       v
Payment Event Processor
       |
       v
Update Unified Transaction
```

The provider module is responsible for:

- Signature verification
- Payload validation
- Provider event mapping

The core system is responsible for:

- Transaction updates
- Event persistence
- Idempotency
- Internal event publishing

---

# 16. OneKhusa Webhook Module

OneKhusa webhook implementation:

```text
/api/v1/webhooks/onekhusa
```

According to the OneKhusa webhook documentation, incoming notifications use a cryptographic HMAC-SHA512 signature and include headers describing the signature and event type.

Reference:

https://docs.onekhusa.com/api-reference/webhooks/overview

Processing flow:

```text
OneKhusa
    |
    v
OneKhusaWebhookController
    |
    v
Verify Signature
    |
    +-- Invalid --> Reject
    |
    +-- Valid
          |
          v
Map to Generic Payment Event
          |
          v
Check Duplicate
          |
          v
Queue Event
          |
          v
HTTP 200
```

The handler should remain lightweight because OneKhusa may retry failed webhook deliveries.

---

# 17. Payment Gateway Capabilities

Different providers support different services.

Use capabilities rather than assuming every gateway supports everything.

Example:

```kotlin
enum class GatewayCapability {
    COLLECTIONS,
    REQUEST_TO_PAY,
    SINGLE_DISBURSEMENT,
    BATCH_DISBURSEMENT,
    WEBHOOKS,
    REFUNDS,
    REVERSALS
}
```

Each provider can declare its capabilities:

```kotlin
interface PaymentGateway {

    fun getGatewayType(): GatewayType

    fun getCapabilities(): Set<GatewayCapability>
}
```

Example:

```text
ONEKHUSA
├── COLLECTIONS
├── REQUEST_TO_PAY
├── SINGLE_DISBURSEMENT
├── BATCH_DISBURSEMENT
└── WEBHOOKS
```

This allows the system to grow without designing every provider around identical features.

---

# 18. Gateway Selection Strategies

The service can support multiple selection methods.

## Explicit Gateway

```json
{
  "gateway": "ONEKHUSA"
}
```

## Default Gateway

The system automatically uses a configured default.

```yaml
payment:
  default-gateway: ONEKHUSA
```

## Smart Routing

Future implementation:

```text
Payment Request
       |
       v
Routing Engine
       |
       +-- Lowest Cost
       |
       +-- Highest Availability
       |
       +-- Payment Method Support
       |
       +-- Currency Support
       |
       v
Selected Gateway
```

The first version should use explicit or configured default gateway selection.

---

# 19. Database Tables

## payment_transactions

```text
id
gateway
gateway_transaction_id
merchant_reference
amount
currency
payment_type
status
idempotency_key
gateway_metadata
created_at
updated_at
completed_at
```

## payment_events

```text
id
transaction_id
gateway
event_type
external_event_id
payload
status
received_at
processed_at
```

## idempotency_keys

```text
id
idempotency_key
request_hash
transaction_id
response_payload
status
created_at
expires_at
```

## gateway_configurations

Optional future multi-tenant configuration:

```text
id
gateway
organisation_id
merchant_account_id
environment
enabled
configuration_reference
created_at
```

Sensitive credentials should be stored in a secrets manager rather than directly in the database.

---

# 20. Internal Events

All providers should publish normalized events.

```text
payment.initiated
payment.pending
payment.success
payment.failed
payment.reversed
payment.expired
```

Example:

```json
{
  "eventId": "uuid",
  "transactionId": "uuid",
  "gateway": "ONEKHUSA",
  "merchantReference": "INV-10001",
  "eventType": "payment.success",
  "occurredAt": "2026-09-03T10:00:00Z"
}
```

Internal applications should consume these generic events rather than provider-specific events.

---

# 21. Implementation Phases

## Phase 1 – Core Platform

Build:

- Spring Boot project
- PostgreSQL
- Flyway
- Generic transaction model
- PaymentGateway interface
- Gateway resolver
- Unified payment API
- Internal idempotency

No provider-specific code should exist inside the core layer.

---

## Phase 2 – OneKhusa Provider

Create:

```text
gateways/onekhusa/
```

Implement:

- OAuth authentication
- Redis token caching
- Collections
- Request To Pay
- Transaction status mapping
- OneKhusa webhook verification
- OneKhusa event mapping

---

## Phase 3 – Asynchronous Processing

Implement:

- RabbitMQ
- Payment events
- Background processing
- Retry policies
- Dead-letter queues
- Duplicate event protection

---

## Phase 4 – Second Gateway

Add:

```text
gateways/gateway-two/
```

The goal of this phase is also to validate the abstraction.

If adding a second provider requires major changes to the core payment architecture, the abstraction should be improved before further providers are added.

---

## Phase 5 – Gateway Routing

Implement:

- Default gateway configuration
- Gateway availability checks
- Gateway capability checks
- Optional smart routing

---

# 22. Testing Strategy

## Core Tests

- Gateway resolver tests
- Transaction lifecycle tests
- Idempotency tests
- Event processing tests

## Provider Contract Tests

Each provider module should have independent tests.

```text
gateways/onekhusa/
    └── OneKhusaGatewayIntegrationTest
```

Tests should verify:

- Authentication
- Request mapping
- Response mapping
- Error handling
- Webhook verification

## Sandbox Tests

OneKhusa sandbox testing should include:

- Request To Pay
- Payment simulation
- Collection webhook
- Duplicate webhook
- Failed transaction
- Reversal scenarios where supported

Reference:

https://docs.onekhusa.com/getting-started/quick-integration

---

# 23. Production Deployment

The service should be deployed as an independent microservice.

```text
Applications
     |
     v
Payment Gateway Service
     |
     +------------------+
     |                  |
     v                  v
OneKhusa             Gateway Two
```

Recommended infrastructure:

```text
Docker
PostgreSQL
Redis
RabbitMQ
Secret Manager
Monitoring
```

Environment configuration:

```text
development
sandbox
staging
production
```

Each gateway should independently support the environments provided by that gateway.

---

# 24. Security Requirements

- Do not expose gateway secrets to clients.
- Use HTTPS/TLS for all external communication.
- Verify provider webhook signatures.
- Encrypt sensitive data where required.
- Use timing-safe signature comparisons.
- Mask secrets in logs.
- Use correlation IDs.
- Maintain audit logs for financial operations.

---

# 25. Final Architecture

```text
                         +----------------------+
                         | Internal Applications|
                         +----------+-----------+
                                    |
                                    v
                         +----------------------+
                         | Unified Payment API  |
                         +----------+-----------+
                                    |
                                    v
                         +----------------------+
                         | Payment Core         |
                         |----------------------|
                         | Transactions         |
                         | Idempotency          |
                         | Gateway Resolver     |
                         | Event Management     |
                         +----------+-----------+
                                    |
                  +-----------------+------------------+
                  |                                    |
                  v                                    v
        +-------------------+              +-------------------+
        | gateways/onekhusa |              | gateways/provider |
        |-------------------|              |-------------------|
        | OAuth             |              | Authentication    |
        | Collections       |              | Payments          |
        | Request To Pay    |              | Webhooks          |
        | Webhooks          |              | Provider Client   |
        +---------+---------+              +---------+---------+
                  |                                    |
                  v                                    v
             OneKhusa API                        Provider API
```

---

# 26. Final Recommendation

The project should be named and designed as a **Payment Gateway Integration Service**, not a OneKhusa Integration Service.

Recommended project concept:

```text
payment-gateway-service
```

OneKhusa should be the first gateway implementation:

```text
payment-gateway-service/
└── gateways/
    └── onekhusa/
```

The most important design rule is:

> **The core payment system must never depend directly on a specific payment gateway. Gateway-specific logic belongs entirely inside the corresponding provider module.**

This architecture allows the service to start with OneKhusa while remaining ready for additional banks, mobile money providers, payment gateways, and other financial integrations.

---

# 27. Key OneKhusa References

- Developer Portal: https://docs.onekhusa.com/
- API Introduction: https://docs.onekhusa.com/api-reference/get-started/introduction
- Quick Integration: https://docs.onekhusa.com/getting-started/quick-integration
- Collections Overview: https://docs.onekhusa.com/api-reference/collections/overview
- Webhooks Overview: https://docs.onekhusa.com/api-reference/webhooks/overview
- Idempotency: https://docs.onekhusa.com/getting-started/idempotency
- OneKhusa SDK Introduction: https://docs.onekhusa.com/sdk/get-started/introduction
- Change Log: https://docs.onekhusa.com/changelog/19august2026

---

## Document Status

**Version:** 2.0  
**Date:** 3 September 2026  
**Project:** Multi-Payment Gateway Integration Service  
**First Gateway Provider:** OneKhusa
