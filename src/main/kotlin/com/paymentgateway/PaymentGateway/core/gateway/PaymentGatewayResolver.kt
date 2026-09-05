package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.exceptions.GatewayNotSupportedException
import org.springframework.stereotype.Component

@Component
class PaymentGatewayResolver(
    private val gateways: List<PaymentGateway>
) {

    fun resolve(gatewayType: GatewayType): PaymentGateway {
        return gateways.firstOrNull { it.getGatewayType() == gatewayType }
            ?: throw GatewayNotSupportedException(gatewayType)
    }

    fun resolveByCapability(capability: com.paymentgateway.PaymentGateway.core.domain.GatewayCapability): PaymentGateway {
        return gateways.firstOrNull { capability in it.getCapabilities() }
            ?: throw GatewayNotSupportedException("No gateway supports capability: $capability")
    }

    fun availableGateways(): List<GatewayType> {
        return gateways.map { it.getGatewayType() }
    }
}
