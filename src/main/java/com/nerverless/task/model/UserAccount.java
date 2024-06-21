package com.nerverless.task.model;

import java.math.BigDecimal;

public record UserAccount(String name, BigDecimal balance, BigDecimal reserve) {
    public UserAccount {
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }
        if (reserve.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Reserve cannot be negative");
        }
    }

    public UserAccount(String name, BigDecimal balance) {
        this(name, balance, BigDecimal.ZERO);
    }

    public UserAccount(String name) {
        this(name, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public UserAccount withBalance(BigDecimal balance) {
        return new UserAccount(name, balance, reserve);
    }

    public UserAccount withReserve(BigDecimal reserve) {
        return new UserAccount(name, balance, reserve);
    }
}