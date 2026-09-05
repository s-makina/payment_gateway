package com.paymentgateway.PaymentGateway.api

import com.paymentgateway.PaymentGateway.core.exceptions.GatewayNotSupportedException
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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                title = "Bad Request",
                status = HttpStatus.BAD_REQUEST.value(),
                errorCode = "E900",
                detail = ex.message ?: "Unsupported gateway"
            )
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.map { 
            "${it.field}: ${it.defaultMessage}" 
        }
        log.warn("Validation failed: {}", errors)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                title = "Bad Request",
                status = HttpStatus.BAD_REQUEST.value(),
                errorCode = "E900",
                detail = "Validation failed",
                errors = errors
            )
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("Illegal argument: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                title = "Bad Request",
                status = HttpStatus.BAD_REQUEST.value(),
                errorCode = "E900",
                detail = ex.message ?: "Invalid request"
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                title = "Internal Server Error",
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                errorCode = "E950",
                detail = "An unexpected error occurred"
            )
        )
    }
}
