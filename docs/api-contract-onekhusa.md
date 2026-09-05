# OneKhusa API Contract — WireMock Stubs

## Overview

This document defines the exact API contracts for OneKhusa endpoints needed by the Payment Gateway Service. Each section includes the request/response schema and a WireMock mapping stub for local development testing.

**Source:** https://docs.onekhusa.com/

---

## 1. Base URLs

| Environment | Base URL |
|---|---|
| Sandbox | `https://api.onekhusa.com/sandbox/v1` |
| Production | `https://api.onekhusa.com/live/v1` |

WireMock will stub the sandbox base URL.

---

## 2. Authentication — Get Access Token

**Endpoint:** `POST /account/getAccessToken`

**Description:** Obtains a JWT access token for authorizing subsequent API requests. Tokens expire in approximately 5 minutes.

### Request

```http
POST /sandbox/v1/account/getAccessToken
Content-Type: application/json
```

```json
{
  "apiKey": "abc123def456ghi789jkl012mno345",
  "apiSecret": "abc123def456ghi789jkl012mno345pqr678stu901vwx234yz",
  "organisationId": "FYH0NTVW0DXK",
  "merchantAccountNumber": 35253486
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| apiKey | string | yes | — | The client API key |
| apiSecret | string | yes | Length: 45 chars | The client API secret |
| organisationId | string | yes | — | The organization identifier |
| merchantAccountNumber | integer | yes | 8 digits, range 10000000–99999999 | The merchant account number |

### Response — Success (200)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vb25la2h1c2Evc2FuZGJveC92MS9hcGkiLCJhdWQiOiJodHRwOi8vb25la2h1c2Evc2FuZGJveC92MS9hcGkiLCJleHAiOjE3MzUyODgwMDB9.signature",
  "expiresOn": "2024-12-26T10:00:00.000Z",
  "expiryInMinutes": 5
}
```

| Field | Type | Description |
|---|---|---|
| accessToken | string | JWT token for API authentication |
| expiresOn | string (date-time) | Token expiration timestamp |
| expiryInMinutes | integer | Token validity duration (always 5) |

### Response — Error (401)

```json
{
  "type": "https://httpstatuses.com/401",
  "title": "Unauthorized",
  "status": 401,
  "errorCode": "E901",
  "detail": "Invalid API credentials",
  "instance": "/sandbox/v1/account/getAccessToken"
}
```

### WireMock Stub

```json
{
  "id": "auth-success",
  "name": "Get Access Token - Success",
  "request": {
    "method": "POST",
    "url": "/sandbox/v1/account/getAccessToken",
    "bodyPatterns": [
      {
        "matchesJsonPath": "$.apiKey"
      },
      {
        "matchesJsonPath": "$.apiSecret"
      },
      {
        "matchesJsonPath": "$.organisationId"
      },
      {
        "matchesJsonPath": "$.merchantAccountNumber"
      }
    ]
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjUwMDAiLCJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjUwMDAiLCJleHAiOjE3MzUyODgwMDB9.stubbed-signature",
      "expiresOn": "2099-12-31T23:59:59.000Z",
      "expiryInMinutes": 5
    }
  }
}
```

---

## 3. Request To Pay — Initiate

**Endpoint:** `POST /collections/requestToPay/initiate`

**Description:** Generates a Timed Account Number (TAN) tied to a transaction amount and unique reference. The TAN expires after 15 minutes. The customer uses this TAN to pay via their bank or MNO.

### Request

```http
POST /sandbox/v1/collections/requestToPay/initiate
Authorization: Bearer <accessToken>
Content-Type: application/json
Accept-Language: en
X-Idempotency-Key: <unique-key>
```

```json
{
  "merchantAccountNumber": 12345678,
  "transactionAmount": 8375000.00,
  "transactionDescription": "Samsung 85inch TV purchase",
  "referenceNumber": "1020XDFS76GS777",
  "capturedBy": "username@example.com"
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| merchantAccountNumber | integer | yes | 8 digits | Active merchant account number |
| transactionAmount | number (decimal) | yes | — | Total checkout amount |
| transactionDescription | string | yes | — | Description of goods/services |
| referenceNumber | string | yes | 5–25 alphanumeric chars | Unique reference for reconciliation |
| capturedBy | string (email) | yes | — | Background user under merchant account |

**Required Headers:**

| Header | Required | Description |
|---|---|---|
| Authorization | yes | `Bearer <accessToken>` |
| Content-Type | yes | `application/json` |
| Accept-Language | no | Default: `en` |
| X-Idempotency-Key | no* | *Required for idempotent requests. Min 15, max 80 chars. Allowed: A–Z, a–z, 0–9, dash. Retained for 24 hours. |

### Response — Success (200)

```json
{
  "merchantAccountNumber": 12345678,
  "timedAccountNumber": "11005533",
  "expiryDate": "2026-01-05T10:01:56.412Z",
  "expiryInMinutes": 15
}
```

| Field | Type | Description |
|---|---|---|
| merchantAccountNumber | integer | The merchant account used |
| timedAccountNumber | string | Random temporary account (always starts with "1") |
| expiryDate | string (date-time) | When the TAN expires |
| expiryInMinutes | integer | TAN validity (always 15) |

### Response — Error (400)

```json
{
  "type": "https://httpstatuses.com/400",
  "title": "Bad Request",
  "status": 400,
  "errorCode": "E900",
  "detail": "Validation failed",
  "instance": "/sandbox/v1/collections/requestToPay/initiate",
  "errors": [
    "Merchant Account Number should be 8 numbers only.",
    "Transaction Amount is required."
  ]
}
```

### Response — Error (409 — Duplicate Idempotency Key)

```json
{
  "type": "https://httpstatuses.com/409",
  "title": "Conflict",
  "status": 409,
  "errorCode": "E907",
  "detail": "Duplicated idempotency key",
  "instance": "/sandbox/v1/collections/requestToPay/initiate"
}
```

### WireMock Stub — Success

```json
{
  "id": "request-to-pay-success",
  "name": "Request To Pay - Success",
  "priority": 1,
  "request": {
    "method": "POST",
    "url": "/sandbox/v1/collections/requestToPay/initiate",
    "headers": {
      "Authorization": {
        "matches": "Bearer .*"
      },
      "Content-Type": {
        "equalTo": "application/json"
      }
    },
    "bodyPatterns": [
      {
        "matchesJsonPath": "$.merchantAccountNumber"
      },
      {
        "matchesJsonPath": "$.transactionAmount"
      },
      {
        "matchesJsonPath": "$.referenceNumber"
      }
    ]
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "merchantAccountNumber": 12345678,
      "timedAccountNumber": "11005533",
      "expiryDate": "2099-12-31T23:59:59.000Z",
      "expiryInMinutes": 15
    }
  }
}
```

### WireMock Stub — Missing Idempotency Key (400)

```json
{
  "id": "request-to-pay-no-idempotency",
  "name": "Request To Pay - Missing Idempotency Key",
  "priority": 2,
  "request": {
    "method": "POST",
    "url": "/sandbox/v1/collections/requestToPay/initiate",
    "headers": {
      "X-Idempotency-Key": {
        "absent": true
      }
    }
  },
  "response": {
    "status": 400,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "type": "https://httpstatuses.com/400",
      "title": "Bad Request",
      "status": 400,
      "errorCode": "E900",
      "detail": "X-Idempotency-Key header is required",
      "instance": "/sandbox/v1/collections/requestToPay/initiate",
      "errors": [
        "X-Idempotency-Key header is required for this endpoint."
      ]
    }
  }
}
```

---

## 4. Get Transaction Status

**Endpoint:** `POST /collections/getTransaction`

**Description:** Retrieves detailed information about a specific collection transaction.

### Request

```http
POST /sandbox/v1/collections/getTransaction
Authorization: Bearer <accessToken>
Content-Type: application/json
Accept-Language: en
```

```json
{
  "merchantAccountNumber": 35253486,
  "transactionReferenceNumber": "B250713MGRTW"
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| merchantAccountNumber | integer | yes | 8 digits, range 10000000–99999999 | Merchant account number |
| transactionReferenceNumber | string | yes | — | Unique transaction reference |

### Response — Success (200)

```json
{
  "beneficiary": {
    "accountNumber": 12346198,
    "accountName": "MERCHANT SANDBOX",
    "amountReceived": 49500,
    "currencyCode": "MWK"
  },
  "source": {
    "accountNumber": "5271306",
    "customerName": "ANGEL BAULENI",
    "amountSent": 50000,
    "currencyCode": "MWK",
    "sourceReferenceNumber": "JF260209114N",
    "connectorId": 212188,
    "connectorName": "National Bank of Malawi"
  },
  "transaction": {
    "transactionReferenceNumber": "CBPC73IQ5U2E",
    "transactionFee": 500,
    "transactionDescription": "Fake Merchant Account Topup",
    "transactionDate": "2026-02-09T15:12:52.8020476+02:00",
    "valueDate": "2026-02-09T15:12:52.8020476+02:00",
    "transactionCode": "BAM",
    "transactionTypeName": "Account To Merchant",
    "transactionStatusCode": "S",
    "transactionStatusName": "Success",
    "bridgeReferenceNumber": "019c4288-9342-7ebd-a947-6d97d4da77ed",
    "responseCode": "S100",
    "responseMessage": "Successful transaction"
  }
}
```

| Field | Type | Description |
|---|---|---|
| beneficiary.accountNumber | integer | Receiving merchant account |
| beneficiary.accountName | string | Merchant account name |
| beneficiary.amountReceived | decimal | Amount received (after fees) |
| beneficiary.currencyCode | string | Currency (e.g. "MWK") |
| source.accountNumber | string | Payer account number |
| source.customerName | string | Payer name |
| source.amountSent | decimal | Amount sent by payer |
| source.sourceReferenceNumber | string | Payer bank reference |
| source.connectorId | integer | Connector identifier |
| source.connectorName | string | Payer institution name |
| transaction.transactionReferenceNumber | string | Unique transaction reference |
| transaction.transactionFee | decimal | Processing fee |
| transaction.transactionDescription | string | Transaction description |
| transaction.transactionDate | string (date-time) | When processed |
| transaction.transactionStatusCode | string | "S" = Success, "F" = Failed, "R" = Reversed |
| transaction.responseCode | string | Response code (e.g. "S100") |
| transaction.responseMessage | string | Human-readable status |

### WireMock Stub — Success

```json
{
  "id": "get-transaction-success",
  "name": "Get Transaction - Success",
  "request": {
    "method": "POST",
    "url": "/sandbox/v1/collections/getTransaction",
    "headers": {
      "Authorization": {
        "matches": "Bearer .*"
      }
    },
    "bodyPatterns": [
      {
        "matchesJsonPath": "$.merchantAccountNumber"
      },
      {
        "matchesJsonPath": "$.transactionReferenceNumber"
      }
    ]
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "beneficiary": {
        "accountNumber": 12346198,
        "accountName": "MERCHANT SANDBOX",
        "amountReceived": 49500,
        "currencyCode": "MWK"
      },
      "source": {
        "accountNumber": "5271306",
        "customerName": "ANGEL BAULENI",
        "amountSent": 50000,
        "currencyCode": "MWK",
        "sourceReferenceNumber": "JF260209114N",
        "connectorId": 212188,
        "connectorName": "National Bank of Malawi"
      },
      "transaction": {
        "transactionReferenceNumber": "CBPC73IQ5U2E",
        "transactionFee": 500,
        "transactionDescription": "Fake Merchant Account Topup",
        "transactionDate": "2026-02-09T15:12:52.802Z",
        "valueDate": "2026-02-09T15:12:52.802Z",
        "transactionCode": "BAM",
        "transactionTypeName": "Account To Merchant",
        "transactionStatusCode": "S",
        "transactionStatusName": "Success",
        "bridgeReferenceNumber": "019c4288-9342-7ebd-a947-6d97d4da77ed",
        "responseCode": "S100",
        "responseMessage": "Successful transaction"
      }
    }
  }
}
```

### WireMock Stub — Not Found (204)

```json
{
  "id": "get-transaction-not-found",
  "name": "Get Transaction - Not Found",
  "priority": 2,
  "request": {
    "method": "POST",
    "url": "/sandbox/v1/collections/getTransaction",
    "bodyPatterns": [
      {
        "matchesJsonPath": "$.transactionReferenceNumber",
        "equalTo": "NONEXISTENT"
      }
    ]
  },
  "response": {
    "status": 204,
    "headers": {}
  }
}
```

---

## 5. Webhook — Collection Events

**Endpoint:** `POST /api/v1/webhooks/onekhusa` (our internal endpoint)

**Description:** OneKhusa sends webhook notifications to our registered callback URL when collection events occur.

### Webhook Headers

| Header | Description | Example |
|---|---|---|
| X-OneKhusa-Webhook-Event | Event type identifier | `payment.success`, `payrequest.success` |
| X-OneKhusa-Webhook-Signature | HMAC-SHA512 signature for verification | `a1b2c3d4...` |

### Webhook Event Types — Collection

| Event Code | Name | Description |
|---|---|---|
| `payment.success` | Successful Collection | Payment completed, funds credited to merchant |
| `payment.reversed` | Reversed Collection | Previously successful payment reversed |
| `payrequest.success` | Successful Request-to-Pay | Request-to-pay transaction completed |
| `payrequest.reversed` | Reversed Request-to-Pay | Previously successful RTP reversed |

### Webhook Payload — payment.success

```json
{
  "connectorId": 892353,
  "sourceAccountNumber": "74629183",
  "sourceAccountName": "OneKhusa Suppliers Ltd",
  "sourceInstitution": "National Bank of Malawi",
  "sourceReferenceNumber": "SRC4K8L2M9Q1Z",
  "beneficiaryAccountNumber": "102345678901",
  "transactionReferenceNumber": "250905SLFVXD",
  "transactionDescription": "Payment for invoice INV-2025-1010",
  "transactionAmount": 320500.75,
  "transactionFee": 1000.00,
  "transactionDate": "2025-10-10T14:50:00Z",
  "transactionStatusCode": "S",
  "transactionCode": "BAM",
  "responseCode": "S100"
}
```

### Webhook Payload — payrequest.success (with metadata)

```json
{
  "connectorId": 892353,
  "sourceAccountNumber": "74629183",
  "sourceAccountName": "OneKhusa Suppliers Ltd",
  "sourceInstitution": "National Bank of Malawi",
  "sourceReferenceNumber": "SRC4K8L2M9Q1Z",
  "beneficiaryAccountNumber": "102345678901",
  "transactionReferenceNumber": "250905SLFVXD",
  "transactionDescription": "Payment for invoice INV-2025-1010",
  "transactionAmount": 320500.75,
  "transactionFee": 1000.00,
  "transactionDate": "2025-10-10T14:50:00Z",
  "transactionStatusCode": "S",
  "transactionCode": "BAM",
  "responseCode": "S100",
  "metaData": {
    "timedAccountNumber": "11005533",
    "referenceNumber": "1020XDFS76GS777",
    "sourceDescription": "Samsung 85inch TV purchase"
  }
}
```

### Webhook Payload — payment.reversed / payrequest.reversed

```json
{
  "connectorId": 247482,
  "sourceAccountNumber": "74629183",
  "sourceAccountName": "OneKhusa Suppliers Ltd",
  "sourceInstitution": "Airtel Money",
  "sourceReferenceNumber": "SRC4K8L2M9Q1Z",
  "beneficiaryAccountNumber": "2659912345678",
  "transactionReferenceNumber": "251014SRTYXB",
  "transactionDescription": "Payment for invoice INV-2025-1010",
  "transactionAmount": 320500.75,
  "transactionFee": 1000.00,
  "transactionDate": "2025-10-10T14:50:00Z",
  "transactionStatusCode": "R",
  "transactionCode": "MWM",
  "responseCode": "S100",
  "metaData": {
    "timedAccountNumber": "11005533",
    "referenceNumber": "1020XDFS76GS777",
    "sourceDescription": "Samsung 85inch TV purchase"
  }
}
```

### Webhook Payload Field Reference

| Field | Type | Description |
|---|---|---|
| connectorId | integer | 6-digit connector identifier |
| sourceAccountNumber | string | Payer account number |
| sourceAccountName | string | Payer account name |
| sourceInstitution | string | Payer's bank/MNO |
| sourceReferenceNumber | string | Payer bank reference |
| beneficiaryAccountNumber | string | Merchant account number |
| transactionReferenceNumber | string | **Unique transaction reference (use for deduplication)** |
| transactionDescription | string | Transaction description |
| transactionAmount | decimal | Transaction amount |
| transactionFee | decimal | Processing fee |
| transactionDate | string (date-time) | ISO 8601 timestamp |
| transactionStatusCode | string | "S" = Success, "F" = Failed, "R" = Reversed |
| transactionCode | string | Transaction type code (e.g. "BAM", "MWM") |
| responseCode | string | Response code (e.g. "S100") |
| metaData | object | Optional. Present on payrequest events |
| metaData.timedAccountNumber | string | The TAN used for this payment |
| metaData.referenceNumber | string | Original reference number from RequestToPay |
| metaData.sourceDescription | string | Original transaction description |

### Webhook Signature Verification

**Algorithm:** HMAC-SHA512

**Input:** Raw request body (string) + webhook secret

**Header:** `X-OneKhusa-Webhook-Signature`

**Verification steps:**
1. Preserve the raw request payload bytes
2. Retrieve the signature from `X-OneKhusa-Webhook-Signature` header
3. Compute `HMAC-SHA512(rawBody, webhookSecret)`
4. Compare using timing-safe comparison
5. Reject if mismatch

### WireMock Stub — payment.success Webhook

```json
{
  "id": "webhook-payment-success",
  "name": "Webhook - payment.success",
  "request": {
    "method": "POST",
    "url": "/api/v1/webhooks/onekhusa",
    "headers": {
      "X-OneKhusa-Webhook-Event": {
        "equalTo": "payment.success"
      }
    }
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "body": "acknowledged"
  }
}
```

### WireMock Stub — payrequest.success Webhook

```json
{
  "id": "webhook-payrequest-success",
  "name": "Webhook - payrequest.success",
  "request": {
    "method": "POST",
    "url": "/api/v1/webhooks/onekhusa",
    "headers": {
      "X-OneKhusa-Webhook-Event": {
        "equalTo": "payrequest.success"
      }
    }
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "body": "acknowledged"
  }
}
```

---

## 6. Error Response Format (RFC 7807)

All OneKhusa errors follow this structure:

```json
{
  "type": "https://httpstatuses.com/{status}",
  "title": "Error Title",
  "status": 400,
  "errorCode": "E900",
  "detail": "Human-readable explanation",
  "instance": "/sandbox/v1/{endpoint}",
  "errors": ["Field-level error 1", "Field-level error 2"]
}
```

### Error Code Reference

| Code | Description |
|---|---|
| E900 | Validation error |
| E901 | Unauthorized access |
| E902 | Forbidden |
| E903 | Resource not found |
| E904 | Request timeout |
| E905 | Rate limit exceeded |
| E906 | Cache service unavailable |
| E907 | Duplicate idempotency key |
| E950 | Internal server error |
| E951 | Under maintenance |
| E952 | Service unavailable |
| E953 | API gateway timeout |

### HTTP Status Codes Used

| Status | Meaning |
|---|---|
| 200 | Success |
| 201 | Created |
| 202 | Accepted (queued for processing) |
| 204 | No Content (e.g. transaction not found) |
| 400 | Bad Request (validation) |
| 401 | Unauthorized (bad credentials) |
| 402 | Request Failed (business rule) |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict (duplicate idempotency) |
| 424 | External Dependency Failed |
| 429 | Rate Limited |
| 5xx | Server Error |

### WireMock Stub — Generic 400 Error

```json
{
  "id": "error-validation",
  "name": "Error - Validation Failed",
  "priority": 10,
  "request": {
    "method": "POST",
    "urlPattern": "/sandbox/v1/.*"
  },
  "response": {
    "status": 400,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "type": "https://httpstatuses.com/400",
      "title": "Bad Request",
      "status": 400,
      "errorCode": "E900",
      "detail": "Validation failed",
      "instance": "/sandbox/v1/",
      "errors": ["Invalid request payload"]
    }
  }
}
```

### WireMock Stub — 401 Unauthorized

```json
{
  "id": "error-unauthorized",
  "name": "Error - Unauthorized",
  "priority": 10,
  "request": {
    "method": "POST",
    "urlPattern": "/sandbox/v1/collections/.*",
    "headers": {
      "Authorization": {
        "absent": true
      }
    }
  },
  "response": {
    "status": 401,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "type": "https://httpstatuses.com/401",
      "title": "Unauthorized",
      "status": 401,
      "errorCode": "E901",
      "detail": "Missing or invalid access token",
      "instance": "/sandbox/v1/collections/"
    }
  }
}
```

---

## 7. Idempotency Key Requirements

**Header:** `X-Idempotency-Key`

| Rule | Value |
|---|---|
| Length | Min 15, max 80 characters |
| Allowed chars | A–Z, a–z, 0–9, dash (-) |
| Case sensitivity | Case-sensitive |
| Retention | 24 hours |
| Uniqueness | Unique per API request |

**Recommended pattern:** `{merchantAccountNumber}-{guid}`

**Example:** `12345678-019b51ad-44fb-73f9-bd09-334479d7ce63`

---

## 8. Webhook Retry Behavior

- OneKhusa waits up to **30 seconds** for a response
- If no response or non-2xx, retries with **exponential backoff** (1 min, 2 min, 4 min, ...)
- Our endpoint must respond with **HTTP 200** and body `"acknowledged"` as fast as possible
- Heavy processing (DB updates, business logic) must happen **asynchronously** after acknowledging

---

## 9. WireMock Setup Summary

### Required Stubs (MVP)

| Stub ID | Endpoint | Purpose |
|---|---|---|
| `auth-success` | POST /account/getAccessToken | Token acquisition |
| `request-to-pay-success` | POST /collections/requestToPay/initiate | Initiate payment |
| `request-to-pay-no-idempotency` | POST /collections/requestToPay/initiate | Missing idempotency key |
| `get-transaction-success` | POST /collections/getTransaction | Status polling |
| `get-transaction-not-found` | POST /collections/getTransaction | Transaction not found |
| `webhook-payment-success` | POST (internal) | Payment success webhook |
| `webhook-payrequest-success` | POST (internal) | RequestToPay success webhook |
| `error-validation` | POST /sandbox/v1/.* | Generic validation error |
| `error-unauthorized` | POST /sandbox/v1/collections/.* | Missing auth token |

### Future Stubs (Phase 2+)

| Stub ID | Endpoint | Purpose |
|---|---|---|
| `webhook-payment-reversed` | POST (internal) | Payment reversal webhook |
| `webhook-payrequest-reversed` | POST (internal) | RTP reversal webhook |
| `batch-disbursement` | POST /disbursements/batch | Batch disbursements |
| `single-disbursement` | POST /disbursements/single | Single disbursements |

---

## Document Status

**Version:** 1.0
**Date:** 3 September 2026
**Source:** OneKhusa Developer Documentation (https://docs.onekhusa.com/)
**Purpose:** WireMock stub definitions for Payment Gateway Service development
