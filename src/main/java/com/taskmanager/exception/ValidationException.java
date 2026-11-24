package com.taskmanager.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when request input fails validation rules (HTTP 400).
 * Collects multiple field-level issues so a single request can report all problems at once.
 */
public class ValidationException extends ApiException {

    /**
     * Represents one field-level validation failure.
     */
    public static final class FieldError {
        private final String field;
        private final String issue;

        public FieldError(String field, String issue) {
            this.field = field;
            this.issue = issue;
        }

        public String getField() { return field; }
        public String getIssue() { return issue; }
    }

    private final List<FieldError> fieldErrors;

    public ValidationException(List<FieldError> fieldErrors) {
        super(400, "VALIDATION_ERROR", "Validation failed");
        this.fieldErrors = Collections.unmodifiableList(new ArrayList<>(fieldErrors));
    }

    /** Convenience constructor for a single field error. */
    public ValidationException(String field, String issue) {
        this(List.of(new FieldError(field, issue)));
    }

    public List<FieldError> getFieldErrors() { return fieldErrors; }
}
