package com.paymentgateway.PaymentGateway.gateways.onekhusa.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Logs the effective non-secret OneKhusa configuration at startup so
 * misconfiguration (e.g. a `capturedBy` that never arrived from `.env`)
 * is visible without guessing. Credentials are never logged.
 */
@Component
@ConditionalOnProperty(prefix = "payment.gateways.onekhusa", name = ["enabled"], havingValue = "true")
class OneKhusaConfigLogger(
    private val properties: OneKhusaProperties
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
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
