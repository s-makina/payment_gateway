package com.paymentgateway.PaymentGateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PaymentGatewayApplication

fun main(args: Array<String>) {
	DotEnvLoader.load()
	runApplication<PaymentGatewayApplication>(*args)
}
