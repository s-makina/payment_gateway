package com.paymentgateway.PaymentGateway.gateways.paychangu.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Fails fast when the enabled Paychangu gateway is missing required
 * configuration (secret key, callback/return URLs), so a blank value surfaces
 * as a startup error instead of a 400 on the first live payment. Also logs the
 * effective non-secret configuration. Credentials are never logged.
 */
@Component
@ConditionalOnProperty(prefix = "payment.gateways.paychangu", name = ["enabled"], havingValue = "true")
class PaychanguConfigLogger(
    private val properties: PaychanguProperties
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        properties.requireValidCheckoutConfig()
        log.info(
            "Paychangu config: environment={}, baseUrl={}, callbackUrl={}, returnUrl={}, secretKeyPresent={}, webhookSecretPresent={}",
            properties.environment,
            properties.baseUrl,
            properties.callbackUrl,
            properties.returnUrl,
            properties.secretKey.isNotBlank(),
            properties.webhookSecret.isNotBlank()
        )
    }
}
