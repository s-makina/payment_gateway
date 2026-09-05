package com.paymentgateway.PaymentGateway.core.exceptions

import java.util.UUID

class TransactionNotFoundException(transactionId: UUID) :
    RuntimeException("Transaction not found: $transactionId")