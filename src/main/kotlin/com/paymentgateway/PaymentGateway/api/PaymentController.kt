package com.paymentgateway.PaymentGateway.api

import com.paymentgateway.PaymentGateway.core.gateway.PaymentGatewayResolver
import com.paymentgateway.PaymentGateway.core.gateway.PaymentRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentResponse
import com.paymentgateway.PaymentGateway.transactions.PaymentStatusResponse
import com.paymentgateway.PaymentGateway.transactions.TransactionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Payments", description = "Initiate payments and query transaction status")
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val transactionService: TransactionService,
    private val gatewayResolver: PaymentGatewayResolver
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Operation(
        summary = "Initiate a payment",
        description = "Creates a transaction and forwards a provider-neutral payment request " +
            "to the selected gateway. Reusing an idempotencyKey returns the original result."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Payment initiated"),
        ApiResponse(
            responseCode = "400",
            description = "Validation failed or gateway not supported",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409",
            description = "Idempotency key conflict",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping
    suspend fun initiatePayment(
        @Valid @RequestBody request: PaymentRequest
    ): ResponseEntity<PaymentResponse> {
        log.info("Initiating payment: reference={}, gateway={}", request.reference, request.gateway)
        val response = transactionService.initiatePayment(request)
        log.info("Payment initiated: transactionId={}, status={}", response.transactionId, response.status)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Get payment status", description = "Returns the current status of a transaction.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Transaction found"),
        ApiResponse(
            responseCode = "404",
            description = "Transaction not found",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @GetMapping("/{transactionId}")
    suspend fun getPaymentStatus(
        @Parameter(description = "Transaction ID returned by POST /api/v1/payments", required = true)
        @PathVariable transactionId: UUID
    ): ResponseEntity<PaymentStatusResponse> {
        val status = transactionService.getPaymentStatus(transactionId)
        log.info("Payment status retrieved: transactionId={}, status={}", transactionId, status.status)
        return ResponseEntity.ok(status)
    }

    @Operation(summary = "List available gateways", description = "Returns the gateway types enabled on this instance.")
    @ApiResponse(responseCode = "200", description = "Gateway list returned")
    @GetMapping("/gateways")
    fun getAvailableGateways(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf("availableGateways" to gatewayResolver.availableGateways().map { it.name })
        )
    }
}
