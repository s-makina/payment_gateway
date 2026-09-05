package com.paymentgateway.PaymentGateway.core.domain

enum class PaymentStatus {
    CREATED,
    INITIATED,
    PENDING,
    AWAITING_CUSTOMER_PAYMENT,
    SUCCESS,
    FAILED,
    REVERSED,
    EXPIRED;

    fun isTerminal(): Boolean = this in setOf(SUCCESS, FAILED, REVERSED, EXPIRED)

    fun canTransitionTo(target: PaymentStatus): Boolean = when (this) {
        CREATED -> target in setOf(INITIATED, FAILED)
        INITIATED -> target in setOf(PENDING, AWAITING_CUSTOMER_PAYMENT, FAILED)
        PENDING -> target in setOf(SUCCESS, FAILED, REVERSED)
        AWAITING_CUSTOMER_PAYMENT -> target in setOf(SUCCESS, FAILED, EXPIRED)
        SUCCESS -> target in setOf(REVERSED)
        FAILED -> false
        REVERSED -> false
        EXPIRED -> false
    }
}
