package com.nerverless.task.model;

import java.util.UUID;


public record TransactionId(UUID id, String userId) {
    public TransactionId {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }
    }
}