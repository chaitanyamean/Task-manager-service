package com.taskmanager.exception;

/**
 * Thrown when the service or an upstream dependency is temporarily unavailable (HTTP 503).
 */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(503, "SERVICE_UNAVAILABLE", message);
    }
}
