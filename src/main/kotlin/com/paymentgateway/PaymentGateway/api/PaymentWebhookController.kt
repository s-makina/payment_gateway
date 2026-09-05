package com.paymentgateway.PaymentGateway.api

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.gateway.GatewayWebhookRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentGatewayResolver
import com.paymentgateway.PaymentGateway.transactions.TransactionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@Tag(name = "Webhooks", description = "Provider webhook callbacks (signature-verified)")
@RestController
@RequestMapping("/api/v1/webhooks")
class PaymentWebhookController(
    private val gatewayResolver: PaymentGatewayResolver,
    private val transactionService: TransactionService,
    private val objectMapper: JsonMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Operation(
        summary = "OneKhusa webhook callback",
        description = "Receives OneKhusa payment events. The signature header is verified " +
            "before the event is processed; duplicate deliveries are acknowledged without re-processing."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Webhook acknowledged"),
        ApiResponse(
            responseCode = "401",
            description = "Invalid webhook signature",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/onekhusa")
    suspend fun handleOneKhusaWebhook(
        @Parameter(description = "Raw webhook JSON payload", required = true)
        @RequestBody rawBody: String,
        @Parameter(description = "OneKhusa event type, e.g. payrequest.success", required = true)
        @RequestHeader("X-OneKhusa-Webhook-Event") eventType: String,
        @Parameter(description = "HMAC-SHA512 hex signature of the raw body", required = false)
        @RequestHeader(value = "X-OneKhusa-Webhook-Signature", required = false) signature: String?
    ): ResponseEntity<String> {
        log.info("Received OneKhusa webhook: eventType={}", eventType)

        val payload: Map<String, Any> = objectMapper.readValue(rawBody)
        val webhookRequest = GatewayWebhookRequest(
            eventType = eventType,
            payload = payload,
            signature = signature,
            rawBody = rawBody
        )

        // Verifies the signature and maps the event to a generic result; throws on invalid signatures.
        val gateway = gatewayResolver.resolve(GatewayType.ONEKHUSA)
        val result = gateway.processWebhook(webhookRequest)

        transactionService.processWebhookEvent(webhookRequest, result)

        log.info(
            "Webhook processed: transactionReference={}, status={}, duplicate={}",
            result.transactionReference, result.newStatus, result.duplicate
        )
        return ResponseEntity.ok("acknowledged")
    }

    @Operation(
        summary = "Paychangu webhook callback",
        description = "Receives Paychangu payment events. The Signature header (HMAC-SHA256 of the raw " +
            "body) is verified before the event is processed; duplicate deliveries are acknowledged without re-processing."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Webhook acknowledged"),
        ApiResponse(
            responseCode = "401",
            description = "Invalid webhook signature",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/paychangu")
    suspend fun handlePaychanguWebhook(
        @Parameter(description = "Raw webhook JSON payload", required = true)
        @RequestBody rawBody: String,
        @Parameter(description = "HMAC-SHA256 hex signature of the raw body", required = false)
        @RequestHeader(value = "Signature", required = false) signature: String?
    ): ResponseEntity<String> {
        val payload: Map<String, Any> = objectMapper.readValue(rawBody)
        // Paychangu sends the event type inside the payload (event_type field).
        val eventType = (payload["event_type"] as? String).takeUnless { it.isNullOrBlank() } ?: "paychangu.unknown"
        log.info("Received Paychangu webhook: eventType={}", eventType)

        val webhookRequest = GatewayWebhookRequest(
            eventType = eventType,
            payload = payload,
            signature = signature,
            rawBody = rawBody
        )

        // Verifies the signature and maps the event to a generic result; throws on invalid signatures.
        val gateway = gatewayResolver.resolve(GatewayType.PAYCHANGU)
        val result = gateway.processWebhook(webhookRequest)

        transactionService.processWebhookEvent(webhookRequest, result)

        log.info(
            "Webhook processed: transactionReference={}, status={}, duplicate={}",
            result.transactionReference, result.newStatus, result.duplicate
        )
        return ResponseEntity.ok("acknowledged")
    }
}
