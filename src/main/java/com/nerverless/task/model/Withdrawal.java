package com.nerverless.task.model;

import java.math.BigDecimal;

import com.nerverless.task.service.WithdrawalService.WithdrawalId;

public record Withdrawal (WithdrawalId withdrawalId, TransactionId transactionId, String accountName, String toAddress,  BigDecimal amount, TransactionStatus status) implements Transaction {
        public Withdrawal {
            if (withdrawalId == null) {
                throw new IllegalArgumentException("withdrawalId cannot be null");
            }
            if (transactionId == null) {
                throw new IllegalArgumentException("transactionId cannot be null");
            }        
            if (accountName == null || accountName.isBlank()) {
                throw new IllegalArgumentException("accountName cannot be null or empty");
            }
            if (toAddress == null || toAddress.isBlank()) {
                throw new IllegalArgumentException("toAddress cannot be null or empty");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("amount must be greater than zero");
            }
            if (status == null) {
                throw new IllegalArgumentException("status cannot be null");
            }
        }
    }