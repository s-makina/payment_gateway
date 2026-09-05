package com.paymentgateway.PaymentGateway.gateways.onekhusa.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Fails fast when the enabled OneKhusa gateway is missing required collection
 * configuration (e.g. a `capturedBy` that never arrived from `.env`), so a blank
 * value surfaces as a startup error instead of a 400 on the first live payment.
 * Also logs the effective non-secret configuration. Credentials are never logged.
 */
@Component
@ConditionalOnProperty(prefix = "payment.gateways.onekhusa", name = ["enabled"], havingValue = "true")
class OneKhusaConfigLogger(
    private val properties: OneKhusaProperties
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        properties.requireValidCollectionConfig()
        log.info(
            "OneKhusa config: environment={}, baseUrl={}, merchantAccountNumber={}, capturedBy='{}', apiKeyPresent={}, apiSecretPresent={}, organisationIdPresent={}, webhookSecretPresent={}",
            properties.environment,
            properties.baseUrl,
            properties.merchantAccountNumber,
            properties.capturedBy,
            properties.apiKey.isNotBlank(),
            properties.apiSecret.isNotBlank(),
            properties.organisationId.isNotBlank(),
            properties.webhookSecret.isNotBlank()
        )
    }
}
