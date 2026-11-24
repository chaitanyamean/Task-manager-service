package com.taskmanager.exception;

/**
 * Thrown when a request conflicts with existing state (HTTP 422).
 * Used, for example, when an idempotency key is reused with different data.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(422, "CONFLICT", message);
    }
}
