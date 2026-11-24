package com.taskmanager.exception;

/**
 * Base exception for all domain errors.
 * Carries an HTTP status code and a machine-readable error code.
 */
public class ApiException extends RuntimeException {

    private final int    httpStatus;
    private final String errorCode;

    public ApiException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode  = errorCode;
    }

    public int    getHttpStatus() { return httpStatus; }
    public String getErrorCode()  { return errorCode; }
}
