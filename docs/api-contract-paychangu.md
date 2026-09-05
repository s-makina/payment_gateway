# Paychangu API Contract

## Overview

This document defines the Paychangu API contracts used by the Payment Gateway Service
adapter (`gateways/paychangu/`). Each section lists the endpoint, the request/response
schema, and the mapping into the provider-neutral core DTOs.

**Source:** https://developer.paychangu.com/reference/introduction
(llms-friendly markdown: append `.md` to any doc URL)

---

## 1. Base URLs

| Environment | Base URL |
|---|---|
| Sandbox/test mode | `https://api.paychangu.com` (test API key) |
| Production | `https://api.paychangu.com` (live API key) |

Paychangu distinguishes test and live mode by the API key used, not by the base URL.

## 2. Authentication

Every request carries a static secret key as a Bearer token. There is no
token-exchange flow (unlike OneKhusa).

```http
Authorization: Bearer {secret_key}
Accept: application/json
Content-Type: application/json
```

Env var: `PAYCHANGU_SECRET_KEY` (from dashboard → Settings → API Keys & Webhooks).

## 3. Response envelope

All responses share a flat envelope:

```json
{
  "status": "success",       // API-level: "success" | "failed"
  "message": "Hosted payment session generated successfully.",
  "data": { ... }            // endpoint-specific payload
}
```

Errors are the same shape with an HTTP 4xx/5xx status, e.g.:

```json
{ "status": "failed", "message": "currency is required", "data": null }
```

Mapped to `GatewayApiException` (HTTP status preserved for 4xx, 5xx collapsed to
`BAD_GATEWAY`), same convention as the OneKhusa adapter.

---

## 4. Hosted Checkout — Initiate Transaction

**Endpoint:** `POST /payment`

**Used for:** `PaymentType.COLLECTION` on `GatewayType.PAYCHANGU`.
Creates a hosted payment session; the customer completes payment on Paychangu's page
(covers mobile money, bank transfer, and cards).

### Request

| Field | Type | Required | Description |
|---|---|---|---|
| amount | string | yes | Amount to charge (sent as string per API contract) |
| currency | string | yes | `MWK` or `USD` |
| tx_ref | string | optional* | Merchant transaction reference, unique per transaction |
| callback_url | string | yes | IPN/redirect URL after payment; `{tx_ref}` appended by Paychangu |
| return_url | string | yes | Redirect URL on cancel/repeated failure |
| first_name / last_name | string | optional | Customer names (from `PaymentRequest.metadata`) |
| email | string | optional | Customer email (from `PaymentRequest.metadata`) |
| customization | object | optional | `{ title, description }` shown on checkout |
| meta | object | optional | Extra metadata (forwarded from `PaymentRequest.metadata`) |

\* Optional per the OpenAPI spec but always sent by this service (sanitized merchant
reference), since we use it as the reconciliation lookup key.

```json
{
  "amount": "1000",
  "currency": "MWK",
  "tx_ref": "INV-10001",
  "first_name": "Kelvin",
  "last_name": "Banda",
  "email": "kelvin@example.com",
  "callback_url": "https://shop.example.com/payments/INV-10001/callback",
  "return_url": "https://shop.example.com/payments/INV-10001/return",
  "customization": { "title": "Test Payment", "description": "Test purchase" }
}
```

### Response (200)

```json
{
  "message": "Hosted payment session generated successfully.",
  "status": "success",
  "data": {
    "event": "checkout.session:created",
    "checkout_url": "https://test-checkout.paychangu.com/7887951180",
    "data": {
      "tx_ref": "98993331-d4f4-4840-899f-7b46cacbb9f4",
      "currency": "MWK",
      "amount": 1000,
      "mode": "sandbox",
      "status": "pending"
    }
  }
}
```

### Core mapping

| Paychangu | Core |
|---|---|
| `checkout_url` | `PaymentResponse.paymentInstructions["checkoutUrl"]` |
| `data.tx_ref` | `PaymentResponse.gatewayTransactionId` (falls back to sanitized merchant reference when absent) |
| — | `PaymentResponse.status = AWAITING_CUSTOMER_PAYMENT` |

---

## 5. Direct MoMo Charge

**Endpoint:** `POST /mobile-money/payments/initialize`

**Used for:** `PaymentType.DIRECT_CHARGE` on `GatewayType.PAYCHANGU`.
Charges the customer's mobile-money wallet server-to-server; the customer confirms via
the operator's USSD/push prompt.

### Request

| Field | Type | Required | Description |
|---|---|---|---|
| mobile | string | yes | Customer phone number (`PaymentRequest.metadata["mobile"]`) |
| mobile_money_operator_ref_id | string | yes | Operator ref from Get Operators (`PaymentRequest.metadata["operatorRefId"]`) |
| amount | string | yes | Amount to charge |
| charge_id | string | yes | Unique identifier for this transaction |
| email / first_name / last_name | string | optional | Customer info |

Operator ref ids come from `GET /mobile-money/operators` (not integrated yet; values
are configured per request through metadata).

`charge_id` is stamped with the `PDC-` prefix (`PaychanguMapper.DIRECT_CHARGE_PREFIX`)
so status polls can be routed to the correct verify endpoint — Paychangu excludes
direct charges from `/verify-payment/{tx_ref}`.

### Response (200)

```json
{
  "status": "success",
  "message": "Payment initiated successfully.",
  "data": {
    "amount": 50,
    "charge_id": "27",
    "ref_id": "95652259752",
    "status": "pending",
    "mobile": "+265997xxxx50",
    "currency": "MWK",
    "mobile_money": { "name": "Airtel Money", "ref_id": "20be6c20-...", "country": "Malawi" }
  }
}
```

### Core mapping

| Paychangu | Core |
|---|---|
| `data.charge_id` | `PaymentResponse.paymentInstructions["gatewayChargeId"]` (observability) |
| prefixed `charge_id` we sent | `PaymentResponse.gatewayTransactionId` (stable lookup key) |
| `data.ref_id` | `paymentInstructions["operatorRefId"]` |
| `data.mobile_money.name` | `paymentInstructions["operator"]` |
| — | `PaymentResponse.status = PENDING` |

---

## 6. Transaction Verification

**Endpoints:**

- `GET /verify-payment/{tx_ref}` — checkout payments
- `GET /mobile-money/payments/{chargeId}/verify` — direct charges

Both return the same `data` shape. The adapter picks the endpoint by reference
prefix (`PDC-` → direct charge), with a fallback retry against the direct-charge
endpoint when `/verify-payment` answers 404 (covers charge ids learned from webhooks).

### Response (200)

```json
{
  "status": "success",
  "message": "Payment details retrieved successfully.",
  "data": {
    "event_type": "checkout.payment",
    "tx_ref": "PA54231315",
    "mode": "live",
    "type": "API Payment (Checkout)",
    "status": "success",
    "number_of_attempts": 1,
    "reference": "26262633201",
    "currency": "MWK",
    "amount": 1000,
    "charges": 40,
    "authorization": { "channel": "Card", "brand": "MASTERCARD", "completed_at": "..." },
    "customer": { "email": "yourmail@example.com", "first_name": "Mac", "last_name": "Phiri" },
    "created_at": "2024-08-08T23:20:21.000000Z",
    "updated_at": "2024-08-08T23:20:21.000000Z"
  }
}
```

A `404` on verification is normalized to `PENDING` (the transaction is simply not
visible/completed yet), matching the OneKhusa 204-handling convention.

### Core mapping

| Paychangu | Core |
|---|---|
| `data.status` | `PaymentStatusResult.status` (`success`→SUCCESS, `failed`/`cancelled`→FAILED, `reversed`→REVERSED, else PENDING) |
| `data.charge_id` → `reference` → `tx_ref` | `PaymentStatusResult.gatewayTransactionId` |
| `data.amount` / `data.currency` | `amount` / `currency` |
| `data.authorization.completed_at` (fallback `updated_at`) | `transactionDate` |
| `data.event_type` | `responseMessage` |
| `channel`, `operator`, `charges`, `numberOfAttempts`, `customerEmail` | `metadata` |

---

## 7. Webhooks

**Our endpoint:** `POST /api/v1/webhooks/paychangu`
**Their header:** `Signature` — HMAC-SHA256 hex of the raw body keyed with the webhook
secret (`PAYCHANGU_WEBHOOK_SECRET`, generated in the dashboard). Verified with a
timing-safe comparison (`PaychanguWebhookVerifier`).

The event type arrives in the payload (`event_type`), not a header. Paychangu retries
non-200 deliveries 3 times at 30-minute intervals; our duplicate detection
(`WebhookEventRepository`) makes replays idempotent.

### Sample payloads

Successful direct API payment:

```json
{
  "event_type": "api.charge.payment",
  "currency": "MWK",
  "amount": 1000,
  "charge": "20",
  "mode": "test",
  "type": "Direct API Payment",
  "status": "success",
  "charge_id": "5d676fg",
  "reference": "71308131545",
  "authorization": { "channel": "Mobile Bank Transfer", "completed_at": "2025-01-15T19:53:18.000000Z" }
}
```

Checkout payment (carries `tx_ref`):

```json
{
  "event_type": "checkout.payment",
  "tx_ref": "ae041eae-6abd-4602-a949-56fbd65c29fe",
  "reference": "26262633201",
  "status": "success",
  "amount": 10000,
  "currency": "MWK"
}
```

API payout (not integrated):

```json
{
  "event_type": "api.payout",
  "charge_id": "4567tfuty",
  "reference": "54438943842",
  "status": "success"
}
```

### Reference resolution order

`tx_ref` → `charge_id` → `reference`. (`reference` alone is a gateway-assigned number
that matches no local identifier, so it is only used as a last resort for events that
carry neither `tx_ref` nor `charge_id`.)

### Core mapping

| Paychangu | Core |
|---|---|
| `event_type` | `WebhookProcessingResult.eventType` |
| `tx_ref`/`charge_id`/`reference` | `WebhookProcessingResult.transactionReference` |
| `status` | `WebhookProcessingResult.newStatus` (same mapping as verification) |

Per Paychangu's "Always Re-query" guidance, webhook-driven status changes can be
confirmed with the verification endpoints; the periodic reconciliation job
(`payment.reconciliation`) already provides the webhook-independent safety net.

---

## 8. Test credentials

| Instrument | Value | Result |
|---|---|---|
| VISA | `4242424242424242`, CVC `123`, 12/30 | 3DS SUCCESS (OTP `1234`) |
| MASTERCARD | `5555555555554444`, CVC `123`, 12/30 | 3DS SUCCESS |
| VISA | `4000000000000002` | 3DS DECLINED |
| Airtel Money | `990000000` | SUCCESS |
| Airtel Money | `990000001` | FAILED |
| TNM Mpamba | `899817565` | SUCCESS |
| TNM Mpamba | `899817566` | FAILED |

Sandbox end-to-end runs are manual/CI-only, per the project testing policy; unit and
integration tests use WireMock stubs mirroring the payloads above.
