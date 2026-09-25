package com.modelflux.exception;

import org.springframework.http.HttpStatusCode;

public class ProviderApiException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public ProviderApiException(String message, HttpStatusCode statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        int value = statusCode.value();
        return value == 429 || value >= 500;
    }
}