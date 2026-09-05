package com.paymentgateway.PaymentGateway.api

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.gateway.GatewayWebhookRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentGatewayResolver
import com.paymentgateway.PaymentGateway.transactions.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@RestController
@RequestMapping("/api/v1/webhooks")
class PaymentWebhookController(
    private val gatewayResolver: PaymentGatewayResolver,
    private val transactionService: TransactionService,
    private val objectMapper: JsonMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/onekhusa")
    suspend fun handleOneKhusaWebhook(
        @RequestBody rawBody: String,
        @RequestHeader("X-OneKhusa-Webhook-Event") eventType: String,
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
}