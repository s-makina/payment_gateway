package com.paymentgateway.PaymentGateway

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = [
        // Keep reconciliation runs out of context-loading tests.
        "payment.reconciliation.enabled=false"
    ]
)
abstract class AbstractIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}