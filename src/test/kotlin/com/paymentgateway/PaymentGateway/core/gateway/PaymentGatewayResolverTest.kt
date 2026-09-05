package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.GatewayCapability
import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.exceptions.GatewayNotSupportedException
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaymentGatewayResolverTest {

    private val oneKhusa = FakeGateway(
        GatewayType.ONEKHUSA,
        setOf(GatewayCapability.REQUEST_TO_PAY, GatewayCapability.WEBHOOKS)
    )
    private val resolver = PaymentGatewayResolver(listOf(oneKhusa))

    @Test
    fun `resolves a known gateway`() {
        assertSame(oneKhusa, resolver.resolve(GatewayType.ONEKHUSA))
    }

    @Test
    fun `throws GatewayNotSupportedException for an unknown gateway`() {
        assertFailsWith<GatewayNotSupportedException> {
            resolver.resolve(GatewayType.GATEWAY_TWO)
        }
    }

    @Test
    fun `resolves by capability`() {
        assertSame(oneKhusa, resolver.resolveByCapability(GatewayCapability.WEBHOOKS))
    }

    @Test
    fun `throws when no gateway supports a capability`() {
        assertFailsWith<GatewayNotSupportedException> {
            resolver.resolveByCapability(GatewayCapability.REFUNDS)
        }
    }

    @Test
    fun `lists available gateways`() {
        assertEquals(listOf(GatewayType.ONEKHUSA), resolver.availableGateways())
    }

    private class FakeGateway(
        private val type: GatewayType,
        private val capabilities: Set<GatewayCapability>
    ) : PaymentGateway {
        override fun getGatewayType(): GatewayType = type
        override fun getCapabilities(): Set<GatewayCapability> = capabilities
        override suspend fun initiatePayment(request: PaymentRequest): PaymentResponse =
            throw UnsupportedOperationException()
        override suspend fun getPaymentStatus(transactionReference: String): PaymentStatusResult =
            throw UnsupportedOperationException()
        override suspend fun processWebhook(request: GatewayWebhookRequest): WebhookProcessingResult =
            throw UnsupportedOperationException()
    }
}