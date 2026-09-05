package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.GatewayCapability
import com.paymentgateway.PaymentGateway.core.domain.GatewayType

interface PaymentGateway {

    fun getGatewayType(): GatewayType

    fun getCapabilities(): Set<GatewayCapability>

    suspend fun initiatePayment(request: PaymentRequest): PaymentResponse

    suspend fun getPaymentStatus(transactionReference: String): PaymentStatusResult

    suspend fun processWebhook(request: GatewayWebhookRequest): WebhookProcessingResult
}
