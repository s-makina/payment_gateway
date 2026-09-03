# OneKhusa Integration Microservice Plan

## 1. Purpose

This document defines the integration plan for a dedicated **OneKhusa Integration Microservice**. The service will centralize communication between internal applications and the OneKhusa Payment Gateway.

The initial implementation focuses on **Collections / Accept Payments**, including Request To Pay, webhook processing, transaction tracking, idempotency, sandbox testing, and production readiness.

---

## 2. Recommended Technology Stack

### Core

- **Language:** Kotlin
- **Framework:** Spring Boot
- **Build Tool:** Gradle
- **Database:** PostgreSQL
- **HTTP Client:** Spring WebClient
- **Database Migration:** Flyway

### Supporting Components

- **Redis:** OAuth access-token caching and optional short-lived request caching
- **RabbitMQ:** Asynchronous webhook and payment-event processing
- **Docker:** Containerized deployment
- **Observability:** Spring Boot Actuator, structured logging, metrics, and alerting

### Why this stack?

Spring Boot is a strong fit for a payment integration service because the integration requires reliable HTTP communication, secure credential handling, transaction persistence, asynchronous processing, retries, and idempotency.

---

# 3. High-Level Architecture

```text
+-------------------------+
| Internal Applications   |
| PAYGO / Web / Mobile    |
+-----------+-------------+
            |
            | Internal API / Events
            v
+-----------------------------------+
| OneKhusa Integration Microservice |
|-----------------------------------|
| Authentication                    |
| Collections                       |
| Request To Pay                    |
| Transaction Tracking              |
| Idempotency                       |
| Webhook Verification              |
| Event Publishing                  |
+-----------+-----------------------+
            |
     +------+------+
     |             |
     v             v
+----------+  +-----------+
|PostgreSQL|  | Redis     |
+----------+  +-----------+
            |
            v
        +--------+
        |RabbitMQ|
        +--------+
            ^
            |
            v
+----------------------+
| OneKhusa API         |
| Sandbox / Production |
+----------------------+
```

---

# 4. Integration Scope – Phase 1

## Included

1. OAuth 2.0 access-token generation and caching
2. Request To Pay integration
3. Collection webhook receiver
4. Webhook signature verification
5. Transaction persistence
6. Idempotency management
7. Duplicate webhook protection
8. Sandbox payment simulation
9. Internal payment-status events

## Deferred

1. Batch disbursements
2. Single disbursements
3. Bill payments
4. Advanced analytics
5. Automated reconciliation dashboards

These can be added after the collections integration is stable.

---

# 5. OneKhusa API References

## 5.1 Developer Portal

OneKhusa Developer Documentation:

https://docs.onekhusa.com/

The platform provides RESTful JSON APIs for payment processing and supports collections, disbursements, webhooks, and related merchant operations.

---

## 5.2 Introduction and Authentication

Reference:

https://docs.onekhusa.com/api-reference/get-started/introduction

OneKhusa uses **OAuth 2.0 / OIDC** authentication. API requests require an access token obtained using the merchant API credentials.

The documentation states that access tokens are valid for approximately **5 minutes**, so the integration service should cache and reuse a valid token rather than requesting a new token for every API request.

### Required configuration

The integration service should securely manage:

```text
ONEKHUSA_API_KEY
ONEKHUSA_API_SECRET
ONEKHUSA_ORGANISATION_ID
ONEKHUSA_MERCHANT_ACCOUNT_NUMBER
ONEKHUSA_WEBHOOK_SECRET
```

### Base URLs

Sandbox:

```text
https://api.onekhusa.com/sandbox/v1
```

Production:

```text
https://api.onekhusa.com/live/v1
```

Production access requires the required KYC and compliance process described in the OneKhusa documentation.

---

# 6. Authentication Design

## 6.1 Token Flow

```text
Application Request
        |
        v
Check Redis
        |
        +-- Valid Token Found ---> Use Token
        |
        +-- Token Missing/Expired
                  |
                  v
          Request Access Token
                  |
                  v
          Cache Token in Redis
                  |
                  v
          Call OneKhusa API
```

## 6.2 Token Service Responsibilities

The `TokenService` should:

1. Check Redis for an existing valid access token.
2. Request a new token only when necessary.
3. Store the token with an expiry shorter than the actual expiry.
4. Prevent multiple simultaneous requests from generating unnecessary tokens.
5. Retry authentication only for transient failures.

Suggested components:

```text
auth/
├── OneKhusaTokenClient
├── TokenService
└── TokenCache
```

---

# 7. Collections / Accept Payments Flow

Reference:

https://docs.onekhusa.com/api-reference/collections/overview

OneKhusa Collections allow merchants to receive payments through supported financial and digital channels.

The general lifecycle is:

```text
Customer Initiates Payment
        |
        v
Payment Sent to Merchant Account
        |
        v
OneKhusa Validates and Credits Payment
        |
        v
Collection Recorded
        |
        v
Webhook Sent to Merchant System
        |
        v
Integration Service Processes Event
        |
        v
Internal Application Updated
```

The service should treat the webhook as the asynchronous confirmation mechanism for completed payments.

---

# 8. Request To Pay Flow

Reference:

https://docs.onekhusa.com/getting-started/quick-integration

The proposed Request To Pay workflow is:

```text
1. Internal application creates payment request
                 |
                 v
2. Integration service generates unique reference
                 |
                 v
3. Create and persist pending transaction
                 |
                 v
4. Add X-Idempotency-Key
                 |
                 v
5. Call OneKhusa Request To Pay
                 |
                 v
6. Receive payment initiation response / TAN
                 |
                 v
7. Return payment instructions to application/customer
                 |
                 v
8. Customer completes payment
                 |
                 v
9. OneKhusa sends webhook
                 |
                 v
10. Verify and process payment
```

The Quick Integration guide states that Request To Pay can generate a **Timed Account Number (TAN)** tied to the transaction and amount. The guide indicates that the TAN expires after **15 minutes**.

The implementation should persist the original transaction reference and any returned TAN/reference so webhook events can be correlated safely.

---

# 9. Idempotency Strategy

Reference:

https://docs.onekhusa.com/getting-started/idempotency

OneKhusa requires idempotency for operations involving irreversible or financial actions.

The client should send:

```http
X-Idempotency-Key: <unique-value>
```

## Internal Implementation

For every payment initiation:

```text
Generate UUID
       |
       v
Store idempotency key
       |
       v
Check whether key already exists
       |
       +-- Exists --> Return original transaction result
       |
       +-- New ----> Call OneKhusa
```

Suggested table:

```sql
idempotency_keys
-------------------------
id
idempotency_key
request_hash
transaction_id
status
response_payload
created_at
expires_at
```

The same key should not be reused for a different request payload.

---

# 10. Webhook Integration

## Reference

Collection webhook documentation:

https://docs.onekhusa.com/api-reference/webhooks/collectionswebhook

Webhook architecture:

https://docs.onekhusa.com/api-reference/webhooks/overview

Webhook event list:

https://docs.onekhusa.com/api-reference/webhooks/webhookevents

## 10.1 Endpoint

Example:

```http
POST /api/v1/webhooks/onekhusa/collections
```

The endpoint must:

1. Be publicly accessible.
2. Use HTTPS.
3. Accept HTTP POST requests.
4. Accept JSON payloads.

## 10.2 Webhook Processing

```text
OneKhusa
    |
    | POST Webhook
    v
Webhook Controller
    |
    v
Read Raw Request Body
    |
    v
Verify HMAC Signature
    |
    +-- Invalid --> Reject
    |
    +-- Valid
          |
          v
Check Duplicate Event
          |
          +-- Duplicate --> HTTP 200
          |
          +-- New
                |
                v
          Persist Webhook Event
                |
                v
          Publish to RabbitMQ
                |
                v
          Return HTTP 200
                |
                v
          Background Consumer
                |
                v
          Update Transaction
                |
                v
          Publish Internal Event
```

---

# 11. Webhook Security

According to the OneKhusa webhook documentation, incoming webhook events include a cryptographic signature.

The service should support verification using the documented webhook signature header:

```text
X-OneKhusa-Webhook-Signature
```

The webhook event header is documented as:

```text
X-OneKhusa-Webhook-Event
```

## Verification Steps

1. Preserve the raw request payload.
2. Retrieve the signature from the request header.
3. Generate the expected HMAC using the raw payload and webhook secret.
4. Compare signatures using a timing-safe comparison.
5. Reject unverified events.
6. Only queue verified events for processing.

**Important:** Never update payment state before webhook verification succeeds.

---

# 12. Non-Blocking Webhook Processing

OneKhusa recommends lightweight webhook handling.

The HTTP controller should not perform:

- Complex business logic
- Long-running database operations
- External API calls
- Email notifications
- Heavy reconciliation processing

Instead:

```text
Verify
  |
  v
Persist
  |
  v
Queue
  |
  v
HTTP 200
  |
  v
Process asynchronously
```

The documentation indicates that the gateway waits for a response and may retry failed or timed-out webhook deliveries. Therefore, the endpoint should acknowledge successfully accepted events quickly.

---

# 13. Duplicate Webhook Protection

Network failures and retries can result in the same webhook being delivered more than once.

The service should use a unique transaction/event reference, such as:

```text
TransactionReferenceNumber
```

Example strategy:

```sql
webhook_events
----------------------------
id
event_type
transaction_reference
payload_hash
payload
signature
status
received_at
processed_at
```

Create a unique constraint:

```sql
UNIQUE(transaction_reference, event_type)
```

When a duplicate event is received:

1. Detect the existing event.
2. Do not process the financial transaction again.
3. Return HTTP 200 where appropriate to acknowledge receipt.

---

# 14. Transaction State Machine

Recommended internal transaction states:

```text
CREATED
   |
   v
INITIATED
   |
   +----------------+
   |                |
   v                v
PENDING          FAILED
   |
   v
PAID
   |
   +--> REVERSED
```

For a Request To Pay transaction:

```text
CREATED
  |
  v
PAYMENT_REQUESTED
  |
  v
AWAITING_CUSTOMER_PAYMENT
  |
  +------------------------+
  |                        |
  v                        v
PAID                     EXPIRED
  |
  v
REVERSED (if applicable)
```

The exact final state should always be based on verified OneKhusa responses or verified webhook events.

---

# 15. Database Design

## transactions

```text
id
external_reference
onekhusa_reference
merchant_account_number
amount
currency
status
payment_type
idempotency_key
created_at
updated_at
paid_at
```

## payment_requests

```text
id
transaction_id
tan
expires_at
request_payload
response_payload
```

## webhook_events

```text
id
event_type
transaction_reference
payload
signature
verification_status
processing_status
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
created_at
expires_at
```

## reconciliation_logs

```text
id
transaction_id
action
status
details
created_at
```

---

# 16. Internal API Design

## Create Payment Request

```http
POST /api/v1/payments/request-to-pay
```

Example internal request:

```json
{
  "amount": 10000,
  "currency": "MWK",
  "reference": "INV-10001",
  "customerId": "customer-123"
}
```

Example response:

```json
{
  "transactionId": "uuid",
  "reference": "INV-10001",
  "status": "AWAITING_CUSTOMER_PAYMENT",
  "paymentInstructions": {}
}
```

## Get Payment Status

```http
GET /api/v1/payments/{transactionId}
```

## Webhook Endpoint

```http
POST /api/v1/webhooks/onekhusa/collections
```

---

# 17. RabbitMQ Events

Recommended internal events:

```text
onekhusa.webhook.received
onekhusa.payment.pending
onekhusa.payment.success
onekhusa.payment.failed
onekhusa.payment.reversed
```

Example event:

```json
{
  "eventId": "uuid",
  "transactionId": "uuid",
  "externalReference": "INV-10001",
  "eventType": "payment.success",
  "occurredAt": "2026-09-03T10:00:00Z"
}
```

Internal services should consume these events rather than directly depending on OneKhusa webhook payload formats.

---

# 18. Recommended Project Structure

```text
onekhusa-integration-service/
│
├── src/main/kotlin/
│   └── com/company/onekhusa/
│
│       ├── auth/
│       │   ├── TokenService
│       │   ├── TokenCache
│       │   └── OneKhusaAuthClient
│       │
│       ├── collections/
│       │   ├── CollectionController
│       │   ├── RequestToPayService
│       │   └── OneKhusaCollectionClient
│       │
│       ├── transactions/
│       │   ├── TransactionService
│       │   ├── TransactionEntity
│       │   └── TransactionRepository
│       │
│       ├── webhooks/
│       │   ├── WebhookController
│       │   ├── WebhookVerifier
│       │   ├── WebhookService
│       │   └── WebhookConsumer
│       │
│       ├── events/
│       │   ├── EventPublisher
│       │   └── PaymentEvents
│       │
│       ├── idempotency/
│       │   └── IdempotencyService
│       │
│       └── config/
│
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│
├── Dockerfile
└── docker-compose.yml
```

---

# 19. Sandbox Testing Plan

Reference:

https://docs.onekhusa.com/getting-started/quick-integration

## Test Sequence

### Step 1: Configure Sandbox

- Create OneKhusa account.
- Configure merchant account.
- Obtain API key and API secret.
- Obtain Organisation ID.
- Obtain Merchant Account Number.

### Step 2: Configure Webhook

- Create a publicly accessible HTTPS endpoint.
- Register the webhook in the OneKhusa portal.
- Subscribe to the required collection events.

### Step 3: Authenticate

- Request an access token.
- Confirm token caching works.

### Step 4: Request To Pay

- Create a transaction through the integration service.
- Generate a unique idempotency key.
- Call the OneKhusa Request To Pay endpoint.
- Persist the returned payment information.

### Step 5: Simulate Payment

Use the OneKhusa sandbox simulation process described in the Collections documentation to simulate a payment.

### Step 6: Validate Webhook

Verify that:

- The webhook reaches the service.
- The signature is verified.
- The event is persisted.
- The event is processed only once.
- The internal transaction becomes `PAID`.

### Step 7: Duplicate Delivery Test

Send the same event multiple times and verify:

```text
Transaction is updated once
Webhook is acknowledged safely
No duplicate internal event is generated
```

---

# 20. Production Readiness Checklist

## Security

- [ ] API secrets stored in a secret manager.
- [ ] No API secret stored in source control.
- [ ] HTTPS enabled.
- [ ] Webhook signatures verified.
- [ ] Sensitive fields masked in logs.
- [ ] Access restricted to authorized internal services.

## Reliability

- [ ] Idempotency implemented.
- [ ] Duplicate webhooks handled.
- [ ] Database transactions used appropriately.
- [ ] Message queue configured.
- [ ] Retry policies configured.
- [ ] Dead-letter queue configured.

## Observability

- [ ] Correlation IDs.
- [ ] Structured logs.
- [ ] Health checks.
- [ ] Metrics.
- [ ] Failed webhook alerts.
- [ ] Failed payment-processing alerts.

---

# 21. Recommended Implementation Phases

## Sprint 1 – Foundation

- Spring Boot project setup
- PostgreSQL
- Flyway migrations
- OneKhusa configuration
- OAuth token service
- Redis token cache

## Sprint 2 – Request To Pay

- Transaction model
- Idempotency support
- OneKhusa API client
- Request To Pay workflow
- Internal payment API

## Sprint 3 – Webhooks

- Public webhook endpoint
- HMAC verification
- Event persistence
- RabbitMQ integration
- Duplicate protection

## Sprint 4 – Testing and Hardening

- Sandbox integration
- Payment simulation
- Failure scenarios
- Retry tests
- Duplicate event tests
- Load testing
- Security review

## Sprint 5 – Production Deployment

- Production configuration
- Secret management
- Monitoring
- KYC/compliance readiness
- Production webhook configuration
- Controlled go-live

---

# 22. Future Expansion

After Collections are stable, the same microservice can be expanded to include:

1. Single Disbursements
2. Batch Disbursements
3. Bill Payments
4. Transaction Inquiry
5. Reporting
6. Reconciliation
7. Merchant account operations

The architecture should keep OneKhusa-specific API models inside the integration layer. Internal applications should communicate using application-specific DTOs and events so that future changes to OneKhusa payloads have minimal impact on the rest of the platform.

---

# 23. Key Documentation References

- Developer Portal: https://docs.onekhusa.com/
- API Introduction: https://docs.onekhusa.com/api-reference/get-started/introduction
- Quick Integration Guide: https://docs.onekhusa.com/getting-started/quick-integration
- Collections Overview: https://docs.onekhusa.com/api-reference/collections/overview
- Collection Webhooks: https://docs.onekhusa.com/api-reference/webhooks/collectionswebhook
- Webhook Architecture: https://docs.onekhusa.com/api-reference/webhooks/overview
- Webhook Events: https://docs.onekhusa.com/api-reference/webhooks/webhookevents
- Idempotency: https://docs.onekhusa.com/getting-started/idempotency

---

# 24. Final Recommendation

Build the initial service as a **Kotlin + Spring Boot microservice focused exclusively on OneKhusa payment integration**.

The first production-ready scope should be:

```text
OAuth Authentication
        +
Token Caching
        +
Request To Pay
        +
Idempotency
        +
Secure Webhooks
        +
Asynchronous Processing
        +
Transaction Persistence
        +
Sandbox Testing
```

This creates a reusable payment integration boundary that can support multiple applications, including PAYGO platforms, web applications, and future products without each application integrating directly with OneKhusa.

---

## Document Status

**Version:** 1.0  
**Date:** 3 September 2026  
**Scope:** OneKhusa Collections Integration Microservice
