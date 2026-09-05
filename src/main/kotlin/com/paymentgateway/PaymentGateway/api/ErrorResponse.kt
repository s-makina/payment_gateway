package com.paymentgateway.PaymentGateway.api

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val type: String = "https://httpstatuses.com/",
    val title: String,
    val status: Int,
    val errorCode: String,
    val detail: String,
    val instance: String? = null,
    val errors: List<String>? = null,
    val timestamp: Instant = Instant.now()
)
