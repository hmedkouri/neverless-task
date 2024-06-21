package com.nerverless.task.model;

import java.math.BigDecimal;

public record Report(TransactionId transactionId, BigDecimal amount, TransactionStatus status, String message) {
    public Report {
        if (transactionId == null) {
            throw new IllegalArgumentException("transaction cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be null or empty");
        }
    }
}