# OneKhusa — Status Lookup by Merchant Reference / TAN (Feature Request)

**To:** OneKhusa Developer Support (payments@onekhusa.com / onekhusa.com/developers)
**From:** Payment Gateway Service integration team
**Date:** 2026-09-05

---

## Context

We are integrating the OneKhusa **Request To Pay** flow (TAN-based) into our payment
gateway service. Our flow:

1. Call `POST /collections/requestToPay/initiate` with a merchant-generated
   `referenceNumber` (e.g. `INV10001`).
2. Receive the `timedAccountNumber` (TAN) and `expiryDate`.
3. Customer pays via their bank / MNO using the TAN.
4. We need to reliably learn whether/when the payment completes.

## The problem

After initiate, the only identifiers we hold are the **merchant `referenceNumber`**
and the **TAN**. Neither appears to be queryable, and the gateway-assigned
`transactionReferenceNumber` is only delivered via webhook. Webhook delivery is not
guaranteed in our deployment, so we need an API-based way to determine completion.

## What we observed in the sandbox (merchant account 76684641)

| Attempt | Request | Result |
|---|---|---|
| Lookup by merchant reference | `getTransaction` with `transactionReferenceNumber = "POLLTEST0001"` (the exact `referenceNumber` we sent at initiate) | **204** — not found, even immediately after a successful initiate |
| Lookup by TAN | `getTransaction` with `transactionReferenceNumber = "11102632"` | **400 E900** — "Transaction Reference Number must be between 12 and 14 characters" (TAN is 8 chars) |
| List + search by merchant reference | `getTransactions` with `searchBy = "TransactionReferenceNumber"`, `searchText = "POLLTEST0001"` | **204** — not found |

The `getTransactions` endpoint's `searchBy` enum only supports
`TransactionReferenceNumber`, `SourceCustomerName`, `Connector.ConnectorName`, and
`SourceAccountNumber` — none of which we know at initiate time — and its responses
do not include the `referenceNumber` or `timedAccountNumber` fields.

## What we're asking

1. **Can `getTransaction` accept the merchant `referenceNumber`** sent at initiate?
   The initiate docs describe `referenceNumber` as a "unique reference for
   reconciliation", which implies it should be usable to look a transaction up. If
   it is supported, does it require the request-to-pay to have completed first?

2. **Can `getTransaction` accept the TAN (`timedAccountNumber`)** as a lookup key?
   If the 12–14 character constraint is fixed, would a TAN lookup work?

3. **Is there a "Get Request To Pay" endpoint** (or similar) that returns the
   status of an initiated request-to-pay keyed by `referenceNumber` or TAN —
   including the gateway-assigned `transactionReferenceNumber` once the customer
   pays?

4. If none of the above exist today: **could `getTransactions` be extended** to
   (a) include initiated request-to-pays, (b) add `ReferenceNumber` and
   `TimedAccountNumber` to the `searchBy` enum, and (c) return those fields in the
   response? That single change would let us poll for completion without webhooks.

## Why it matters

With the current API surface, the **webhook is the only channel** that delivers the
gateway-assigned `transactionReferenceNumber`, and polling cannot bootstrap
discovery of it. If webhooks are missed (no public callback endpoint, network
issues, retry exhaustion), a paid transaction is never reconciled — the merchant is
not credited in our records and the request-to-pay is eventually expired locally.
A reference/TAN-based lookup would give us a reliable, webhook-independent
reconciliation path.

---

*Tested against the sandbox API on 2026-09-05 with merchant account 76684641.*