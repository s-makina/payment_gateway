package com.paymentgateway.PaymentGateway.gateways.paychangu

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import com.paymentgateway.PaymentGateway.core.gateway.PaymentRequest
import com.paymentgateway.PaymentGateway.gateways.paychangu.config.PaychanguProperties
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguCheckoutData
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguDirectChargeData
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguTransactionData
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaychanguMapperTest {

    private val mapper = PaychanguMapper(
        PaychanguProperties(
            secretKey = "sec-test-key",
            callbackUrl = "https://shop.example.com/callback",
            returnUrl = "https://shop.example.com/return"
        )
    )

    private val request = PaymentRequest(
        gateway = GatewayType.PAYCHANGU,
        paymentType = PaymentType.COLLECTION,
        amount = BigDecimal("1000.50"),
        currency = "MWK",
        reference = "INV-10001",
        description = "Test purchase",
        metadata = mapOf("firstName" to "Kelvin", "lastName" to "Banda", "email" to "kelvin@example.com")
    )

    @Test
    fun `maps request to checkout format with config callback urls`() {
        val mapped = mapper.toCheckoutRequest(request)
        assertEquals("1000.50", mapped.amount)
        assertEquals("MWK", mapped.currency)
        assertEquals("INV-10001", mapped.txRef)
        assertEquals("https://shop.example.com/callback", mapped.callbackUrl)
        assertEquals("https://shop.example.com/return", mapped.returnUrl)
        assertEquals("Kelvin", mapped.firstName)
        assertEquals("Banda", mapped.lastName)
        assertEquals("kelvin@example.com", mapped.email)
        assertEquals("Test purchase", mapped.customization?.description)
    }

    @Test
    fun `request callback urls override the configured defaults`() {
        val mapped = mapper.toCheckoutRequest(
            request.copy(
                callbackUrl = "https://merchant.example.com/pay/1/callback",
                returnUrl = "https://merchant.example.com/pay/1/return"
            )
        )
        assertEquals("https://merchant.example.com/pay/1/callback", mapped.callbackUrl)
        assertEquals("https://merchant.example.com/pay/1/return", mapped.returnUrl)
    }

    @Test
    fun `checkout request fails when required config is missing`() {
        val mapper = PaychanguMapper(PaychanguProperties(secretKey = "sec-test"))
        assertFailsWith<IllegalArgumentException> {
            mapper.toCheckoutRequest(request)
        }
    }

    @Test
    fun `sanitizeTxRef strips unsupported characters and truncates`() {
        assertEquals("INV-10001", mapper.sanitizeTxRef("INV-10001"))
        assertEquals("INV10001", mapper.sanitizeTxRef("INV 10001!"))
        assertEquals(100, mapper.sanitizeTxRef("A".repeat(150)).length)
    }

    @Test
    fun `sanitizeChargeId stamps the direct charge prefix`() {
        assertEquals("PDC-INV-10001", mapper.sanitizeChargeId("INV-10001"))
    }

    @Test
    fun `maps checkout response to provider-neutral payment response`() {
        val checkout = PaychanguCheckoutData(
            event = "checkout.session:created",
            checkoutUrl = "https://test-checkout.paychangu.com/7887951180",
            inner = PaychanguCheckoutData.PaychanguCheckoutInner(
                txRef = "ae041eae-6abd-4602-a949-56fbd65c29fe",
                currency = "MWK",
                amount = BigDecimal("1000"),
                mode = "sandbox",
                status = "pending"
            )
        )
        val response = mapper.toPaymentResponse(checkout, request)
        assertEquals(PaymentStatus.AWAITING_CUSTOMER_PAYMENT, response.status)
        assertEquals("INV-10001", response.reference)
        assertEquals(
            "https://test-checkout.paychangu.com/7887951180",
            response.paymentInstructions?.get("checkoutUrl")
        )
        assertEquals("sandbox", response.paymentInstructions?.get("mode"))
        // The gateway-side tx_ref is learned from the inner payload...
        assertEquals("ae041eae-6abd-4602-a949-56fbd65c29fe", response.gatewayTransactionId)
    }

    @Test
    fun `falls back to the sanitized merchant reference when no gateway tx_ref is assigned`() {
        val checkout = PaychanguCheckoutData(
            checkoutUrl = "https://test-checkout.paychangu.com/7887951180"
        )
        val response = mapper.toPaymentResponse(checkout, request)
        assertEquals("INV-10001", response.gatewayTransactionId)
    }

    @Test
    fun `maps request to direct charge format with prefixed charge id`() {
        val mapped = mapper.toDirectChargeRequest(
            request.copy(
                paymentType = PaymentType.DIRECT_CHARGE,
                metadata = mapOf(
                    "mobile" to "265990000000",
                    "operatorRefId" to "20be6c20-adeb-4b5b-a7ba-0769820df4fb",
                    "email" to "kelvin@example.com"
                )
            )
        )
        assertEquals("265990000000", mapped.mobile)
        assertEquals("20be6c20-adeb-4b5b-a7ba-0769820df4fb", mapped.mobileMoneyOperatorRefId)
        assertEquals("1000.50", mapped.amount)
        assertEquals("PDC-INV-10001", mapped.chargeId)
        assertEquals("kelvin@example.com", mapped.email)
    }

    @Test
    fun `direct charge request fails without mobile and operator ref id`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            mapper.toDirectChargeRequest(request.copy(paymentType = PaymentType.DIRECT_CHARGE, metadata = null))
        }
        assertEquals("metadata.mobile is required for PAYCHANGU direct charge payments", ex.message)
        assertFailsWith<IllegalArgumentException> {
            mapper.toDirectChargeRequest(
                request.copy(paymentType = PaymentType.DIRECT_CHARGE, metadata = mapOf("mobile" to "265990000000"))
            )
        }
    }

    @Test
    fun `maps direct charge response to provider-neutral payment response`() {
        val charge = PaychanguDirectChargeData(
            chargeId = "27",
            refId = "95652259752",
            status = "pending",
            mobile = "+265997xxxx50",
            currency = "MWK",
            amount = BigDecimal("1000.50"),
            mobileMoney = com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguMobileMoney(
                name = "Airtel Money"
            )
        )
        val response = mapper.toDirectChargeResponse(charge, request)
        assertEquals(PaymentStatus.PENDING, response.status)
        // The prefixed charge_id we issued is the stable lookup key; the
        // gateway-echoed value is exposed in the instructions.
        assertEquals("PDC-INV-10001", response.gatewayTransactionId)
        assertEquals("95652259752", response.paymentInstructions?.get("operatorRefId"))
        assertEquals("Airtel Money", response.paymentInstructions?.get("operator"))
        assertEquals("+265997xxxx50", response.paymentInstructions?.get("mobile"))
        assertEquals("27", response.paymentInstructions?.get("gatewayChargeId"))
    }

    @Test
    fun `maps transaction statuses`() {
        assertEquals(PaymentStatus.SUCCESS, mapper.mapStatus("success"))
        assertEquals(PaymentStatus.SUCCESS, mapper.mapStatus("successful"))
        assertEquals(PaymentStatus.FAILED, mapper.mapStatus("failed"))
        assertEquals(PaymentStatus.FAILED, mapper.mapStatus("cancelled"))
        assertEquals(PaymentStatus.REVERSED, mapper.mapStatus("reversed"))
        assertEquals(PaymentStatus.PENDING, mapper.mapStatus("pending"))
        assertEquals(PaymentStatus.PENDING, mapper.mapStatus(null))
        assertEquals(PaymentStatus.PENDING, mapper.mapStatus("something-new"))
    }

    @Test
    fun `maps verify response to provider-neutral status result`() {
        val transaction = PaychanguTransactionData(
            txRef = "PA54231315",
            status = "success",
            reference = "26262633201",
            currency = "MWK",
            amount = BigDecimal("1000"),
            charges = BigDecimal("40"),
            eventType = "checkout.payment",
            authorization = com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguAuthorization(
                channel = "Card",
                brand = "MASTERCARD",
                completedAt = "2024-08-08T23:21:22.000000Z"
            ),
            customer = com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguCustomer(
                email = "yourmail@example.com"
            )
        )
        val result = mapper.toPaymentStatusResult(transaction, "PA54231315")
        assertEquals(PaymentStatus.SUCCESS, result.status)
        // charge_id is absent on checkout verifications, so reference wins.
        assertEquals("26262633201", result.gatewayTransactionId)
        assertEquals(BigDecimal("1000"), result.amount)
        assertEquals("MWK", result.currency)
        assertEquals("Card", result.metadata?.get("channel"))
        assertEquals("MASTERCARD", result.metadata?.get("brand"))
        assertEquals("yourmail@example.com", result.metadata?.get("customerEmail"))
    }

    @Test
    fun `verify result prefers charge_id then reference then tx_ref`() {
        val full = PaychanguTransactionData(
            chargeId = "2345",
            reference = "26262633201",
            txRef = "PA54231315",
            status = "success"
        )
        val result = mapper.toPaymentStatusResult(full, "PA54231315")
        assertEquals("2345", result.gatewayTransactionId)

        val unknown = mapper.toPaymentStatusResult(PaychanguTransactionData(status = "pending"), "GWY-REF")
        assertEquals("GWY-REF", unknown.gatewayTransactionId)
    }

    @Test
    fun `metadata entries with null values are omitted`() {
        val transaction = PaychanguTransactionData(status = "success")
        val result = mapper.toPaymentStatusResult(transaction, "REF")
        assertNull(result.metadata?.get("channel"))
        assertNull(result.metadata?.get("operator"))
    }
}
