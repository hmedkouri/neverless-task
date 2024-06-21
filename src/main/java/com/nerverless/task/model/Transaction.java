package com.nerverless.task.model;

import java.math.BigDecimal;

public interface Transaction {

    public TransactionId transactionId();
    public BigDecimal amount();

    record Transfer (TransactionId transactionId, String fromAccountName, String toAccountName, BigDecimal amount) implements Transaction {
        public Transfer {
            if (transactionId == null) {
                throw new IllegalArgumentException("transactionId cannot be null");
            }        
            if (fromAccountName == null || fromAccountName.isBlank()) {
                throw new IllegalArgumentException("fromAccountName cannot be null or empty");
            }
            if (toAccountName == null || toAccountName.isBlank()) {
                throw new IllegalArgumentException("toAccountName cannot be null or empty");
            }
            if (fromAccountName.equals(toAccountName)) {
                throw new IllegalArgumentException("fromAccountName and toAccountName cannot be the same");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("amount must be greater than zero");
            }
        }
    }

    record WithdrawalRequest (TransactionId transactionId, String accountName, String toAddress,  BigDecimal amount) implements Transaction {
        public WithdrawalRequest {
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
        }
    }
}