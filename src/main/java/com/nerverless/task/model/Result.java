package com.nerverless.task.model;

public record Result<T>(T value, String message) {
    public Result {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be null or empty");
        }
    }
}