package com.taskmanager.api;

/**
 * Wrapper returned by every TaskApi operation.
 * Holds an HTTP-style status code and a JSON-style body string.
 */
public final class ApiResponse {

    private final int    statusCode;
    private final String body;

    public ApiResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body       = body;
    }

    public int    getStatusCode() { return statusCode; }
    public String getBody()       { return body; }

    /**
     * Convenience: returns the response as a readable string
     * that resembles what an HTTP client would print.
     */
    public String toJson() {
        return "HTTP " + statusCode + "\n" + body;
    }

    @Override
    public String toString() {
        return toJson();
    }

    // ── Static factory helpers ────────────────────────────────────────────────

    public static ApiResponse ok(String body)      { return new ApiResponse(200, body); }
    public static ApiResponse created(String body) { return new ApiResponse(201, body); }
    public static ApiResponse badRequest(String body)  { return new ApiResponse(400, body); }
    public static ApiResponse notFound(String body)    { return new ApiResponse(404, body); }
    public static ApiResponse conflict(String body)    { return new ApiResponse(422, body); }
    public static ApiResponse badGateway(String body)  { return new ApiResponse(502, body); }
    public static ApiResponse unavailable(String body) { return new ApiResponse(503, body); }
    public static ApiResponse internalError(String body) { return new ApiResponse(500, body); }
}
