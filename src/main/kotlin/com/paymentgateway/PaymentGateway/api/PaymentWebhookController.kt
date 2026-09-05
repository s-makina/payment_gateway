package com.paymentgateway.PaymentGateway.api

import com.paymentgateway.PaymentGateway.core.gateway.GatewayWebhookRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentGatewayResolver
import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/webhooks")
class PaymentWebhookController(
    private val gatewayResolver: PaymentGatewayResolver
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/onekhusa")
    suspend fun handleOneKhusaWebhook(
        @RequestBody payload: Map<String, Any>,
        @RequestHeader("X-OneKhusa-Webhook-Event") eventType: String,
        @RequestHeader("X-OneKhusa-Webhook-Signature") signature: String?
    ): ResponseEntity<String> {
        log.info("Received OneKhusa webhook: eventType={}", eventType)
        
        val gateway = gatewayResolver.resolve(GatewayType.ONEKHUSA)
        
        val webhookRequest = GatewayWebhookRequest(
            eventType = eventType,
            payload = payload,
            signature = signature
        )
        
        val result = gateway.processWebhook(webhookRequest)
        
        log.info("Webhook processed: transactionReference={}, status={}, duplicate={}", 
            result.transactionReference, result.newStatus, result.duplicate)
        
        return ResponseEntity.ok("acknowledged")
    }
}
