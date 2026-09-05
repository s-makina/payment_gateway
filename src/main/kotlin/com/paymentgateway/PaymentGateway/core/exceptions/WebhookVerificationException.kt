package com.paymentgateway.PaymentGateway.core.exceptions

/**
 * Thrown when an incoming webhook fails signature verification.
 * Webhook state must never be updated before verification succeeds.
 */
class WebhookVerificationException(message: String) : RuntimeException(message)