# Payment Gateway

Multi-payment gateway integration microservice. First provider is OneKhusa; the core stays provider-neutral.

## Language

**Tenant**:
A merchant business onboarded on the gateway. Owns its transactions, idempotency keys, and webhook events.
_Avoid_: Organization, client, account, OneKhusa organisation
