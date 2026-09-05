package com.paymentgateway.PaymentGateway.core.domain

enum class GatewayCapability {
    COLLECTIONS,
    REQUEST_TO_PAY,
    SINGLE_DISBURSEMENT,
    BATCH_DISBURSEMENT,
    WEBHOOKS,
    REFUNDS,
    REVERSALS
}
