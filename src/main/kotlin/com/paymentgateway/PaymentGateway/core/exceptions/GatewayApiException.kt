package com.paymentgateway.PaymentGateway.core.exceptions

import org.springframework.http.HttpStatus

/**
 * Thrown when an upstream payment gateway API call fails.
 * Provider adapters wrap provider-specific errors in this generic type so the
 * core/API layers never depend on provider-specific error models.
 */
class GatewayApiException(
    val status: HttpStatus,
    val errorCode: String,
    override val message: String
) : RuntimeException(message)