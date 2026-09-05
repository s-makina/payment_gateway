package com.paymentgateway.PaymentGateway.api

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "RFC 7807-style error response")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    @Schema(description = "Problem type URI", example = "https://httpstatuses.com/")
    val type: String = "https://httpstatuses.com/",
    @Schema(description = "HTTP reason phrase", example = "Bad Request")
    val title: String,
    @Schema(description = "HTTP status code", example = "400")
    val status: Int,
    @Schema(description = "Service error code", example = "E900")
    val errorCode: String,
    @Schema(description = "Human-readable detail", example = "Validation failed")
    val detail: String,
    @Schema(description = "Request instance identifier")
    val instance: String? = null,
    @Schema(description = "Field-level validation errors")
    val errors: List<String>? = null,
    @Schema(description = "Error timestamp")
    val timestamp: Instant = Instant.now()
)
