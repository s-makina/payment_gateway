package com.paymentgateway.PaymentGateway.core.exceptions

import com.paymentgateway.PaymentGateway.core.domain.GatewayType

class GatewayNotSupportedException : RuntimeException {

    constructor(gatewayType: GatewayType) :
        super("Gateway not supported: ${gatewayType.name}")

    constructor(message: String) :
        super(message)
}
