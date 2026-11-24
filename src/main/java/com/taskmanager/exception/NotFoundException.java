package com.taskmanager.exception;

/**
 * Thrown when a requested resource does not exist (HTTP 404).
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String resourceType, String id) {
        super(404, "NOT_FOUND", resourceType + " not found with id: " + id);
    }
}
