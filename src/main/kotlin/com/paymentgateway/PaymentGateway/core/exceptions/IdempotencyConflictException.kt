package com.paymentgateway.PaymentGateway.core.exceptions

class IdempotencyConflictException(key: String) :
    RuntimeException("Idempotency key already used with a different request payload: $key")