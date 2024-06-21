package com.nerverless.task.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface WithdrawalService {
    void requestWithdrawal(WithdrawalId id, Address address, BigDecimal amount);
    WithdrawalState getRequestState(WithdrawalId id);

    enum WithdrawalState {
        PROCESSING, COMPLETED, FAILED
    }

    record WithdrawalId(UUID value) {}
    record Address(String value) {}
}

