package com.paymentgateway.PaymentGateway.api

import com.paymentgateway.PaymentGateway.core.exceptions.GatewayApiException
import com.paymentgateway.PaymentGateway.core.exceptions.GatewayNotSupportedException
import com.paymentgateway.PaymentGateway.core.exceptions.IdempotencyConflictException
import com.paymentgateway.PaymentGateway.core.exceptions.TransactionNotFoundException
import com.paymentgateway.PaymentGateway.core.exceptions.WebhookVerificationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(GatewayNotSupportedException::class)
    fun handleGatewayNotSupported(ex: GatewayNotSupportedException): ResponseEntity<ErrorResponse> {
        log.warn("Gateway not supported: {}", ex.message)
        return errorResponse(HttpStatus.BAD_REQUEST, "E900", ex.message ?: "Unsupported gateway")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.map {
            "${it.field}: ${it.defaultMessage}"
        }
        log.warn("Validation failed: {}", errors)
        return errorResponse(HttpStatus.BAD_REQUEST, "E900", "Validation failed", errors)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("Illegal argument: {}", ex.message)
        return errorResponse(HttpStatus.BAD_REQUEST, "E900", ex.message ?: "Invalid request")
    }

    @ExceptionHandler(TransactionNotFoundException::class)
    fun handleTransactionNotFound(ex: TransactionNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Transaction not found: {}", ex.message)
        return errorResponse(HttpStatus.NOT_FOUND, "E903", ex.message ?: "Transaction not found")
    }

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException): ResponseEntity<ErrorResponse> {
        log.warn("Idempotency conflict: {}", ex.message)
        return errorResponse(HttpStatus.CONFLICT, "E907", ex.message ?: "Idempotency key conflict")
    }

    @ExceptionHandler(WebhookVerificationException::class)
    fun handleWebhookVerification(ex: WebhookVerificationException): ResponseEntity<ErrorResponse> {
        log.warn("Webhook verification failed: {}", ex.message)
        return errorResponse(HttpStatus.UNAUTHORIZED, "E901", ex.message ?: "Invalid webhook signature")
    }

    @ExceptionHandler(GatewayApiException::class)
    fun handleGatewayApi(ex: GatewayApiException): ResponseEntity<ErrorResponse> {
        log.warn("Upstream gateway error: code={}, detail={}", ex.errorCode, ex.message)
        return errorResponse(ex.status, ex.errorCode, ex.message)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        log.warn("Illegal state: {}", ex.message)
        return errorResponse(HttpStatus.CONFLICT, "E900", ex.message ?: "Invalid operation")
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "E950", "An unexpected error occurred")
    }

    private fun errorResponse(
        status: HttpStatus,
        errorCode: String,
        detail: String,
        errors: List<String>? = null
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(status).body(
            ErrorResponse(
                title = status.reasonPhrase,
                status = status.value(),
                errorCode = errorCode,
                detail = detail,
                errors = errors
            )
        )
    }
}