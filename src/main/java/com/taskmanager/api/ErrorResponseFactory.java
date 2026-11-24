package com.taskmanager.api;

import com.taskmanager.exception.ApiException;
import com.taskmanager.exception.ValidationException;

/**
 * Single place that converts any ApiException (or unknown Throwable)
 * into the mandated error JSON shape:
 *
 * <pre>
 * {
 *   "error": {
 *     "code": "VALIDATION_ERROR",
 *     "message": "Validation failed",
 *     "details": [{ "field": "title", "issue": "must not be empty" }]
 *   }
 * }
 * </pre>
 *
 * code and message are always strings. details is present for
 * ValidationException and omitted for other error types.
 */
public final class ErrorResponseFactory {

    // Utility class — no instances
    private ErrorResponseFactory() {}

    /**
     * Converts a known ApiException into an ApiResponse with the standard error shape.
     */
    public static ApiResponse from(ApiException ex) {
        String body = buildBody(ex);
        return new ApiResponse(ex.getHttpStatus(), body);
    }

    /**
     * Converts an unexpected Throwable into a 500 Internal Server Error response.
     */
    public static ApiResponse fromUnknown(Throwable t) {
        String body = buildUnknownBody(t);
        return new ApiResponse(500, body);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String buildBody(ApiException ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"error\": {\n");
        sb.append("    \"code\": \"").append(escape(ex.getErrorCode())).append("\",\n");
        sb.append("    \"message\": \"").append(escape(ex.getMessage())).append("\"");

        // Append field-level details only for ValidationException
        if (ex instanceof ValidationException ve && !ve.getFieldErrors().isEmpty()) {
            sb.append(",\n    \"details\": [\n");
            var errors = ve.getFieldErrors();
            for (int i = 0; i < errors.size(); i++) {
                ValidationException.FieldError fe = errors.get(i);
                sb.append("      { \"field\": \"")
                  .append(escape(fe.getField()))
                  .append("\", \"issue\": \"")
                  .append(escape(fe.getIssue()))
                  .append("\" }");
                if (i < errors.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("    ]");
        }

        sb.append("\n  }\n}");
        return sb.toString();
    }

    private static String buildUnknownBody(Throwable t) {
        String message = t.getMessage() != null ? t.getMessage() : "An unexpected error occurred";
        return "{\n" +
               "  \"error\": {\n" +
               "    \"code\": \"INTERNAL_ERROR\",\n" +
               "    \"message\": \"" + escape(message) + "\"\n" +
               "  }\n" +
               "}";
    }

    /** Escapes characters that would break a JSON string literal. */
    private static String escape(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
