package com.taskmanager.exception;

/**
 * Thrown when an upstream dependency (e.g. the LLM provider) returns an error (HTTP 502).
 */
public class BadGatewayException extends ApiException {

    public BadGatewayException(String message) {
        super(502, "BAD_GATEWAY", message);
    }
}
