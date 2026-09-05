package com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response

import java.math.BigDecimal

data class OneKhusaTransactionResponse(
    val beneficiary: Beneficiary? = null,
    val source: Source? = null,
    val transaction: TransactionInfo? = null
) {

    data class Beneficiary(
        val accountNumber: Long? = null,
        val accountName: String? = null,
        val amountReceived: BigDecimal? = null,
        val currencyCode: String? = null
    )

    data class Source(
        val accountNumber: String? = null,
        val customerName: String? = null,
        val amountSent: BigDecimal? = null,
        val currencyCode: String? = null,
        val sourceReferenceNumber: String? = null,
        val connectorId: Long? = null,
        val connectorName: String? = null
    )

    data class TransactionInfo(
        val transactionReferenceNumber: String? = null,
        val transactionFee: BigDecimal? = null,
        val transactionDescription: String? = null,
        val transactionDate: String? = null,
        val transactionStatusCode: String? = null,
        val transactionStatusName: String? = null,
        val responseCode: String? = null,
        val responseMessage: String? = null
    )
}