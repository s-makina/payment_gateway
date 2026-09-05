package com.paymentgateway.PaymentGateway.api

import com.paymentgateway.PaymentGateway.core.gateway.PaymentGatewayResolver
import com.paymentgateway.PaymentGateway.core.gateway.PaymentRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val gatewayResolver: PaymentGatewayResolver
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    suspend fun initiatePayment(
        @RequestBody request: PaymentRequest
    ): ResponseEntity<PaymentResponse> {
        log.info("Initiating payment: reference={}, gateway={}", request.reference, request.gateway)
        
        val gateway = gatewayResolver.resolve(request.gateway)
        val response = gateway.initiatePayment(request)
        
        log.info("Payment initiated: transactionId={}, status={}", response.transactionId, response.status)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{transactionId}")
    suspend fun getPaymentStatus(
        @PathVariable transactionId: UUID
    ): ResponseEntity<Map<String, Any>> {
        log.info("Getting payment status: transactionId={}", transactionId)
        
        // TODO: Look up transaction from database and get status from gateway
        return ResponseEntity.ok(mapOf(
            "transactionId" to transactionId.toString(),
            "status" to "PENDING",
            "message" to "Status lookup not yet implemented"
        ))
    }

    @GetMapping("/gateways")
    fun getAvailableGateways(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "availableGateways" to gatewayResolver.availableGateways().map { it.name }
        ))
    }
}
