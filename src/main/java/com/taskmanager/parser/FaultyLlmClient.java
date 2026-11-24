package com.taskmanager.parser;

import com.taskmanager.exception.BadGatewayException;
import com.taskmanager.exception.ServiceUnavailableException;

/**
 * Test double that simulates bad or failed LLM responses.
 * Used to verify the "never trust the model" rule and error-path handling.
 *
 * <pre>
 * // Returns empty title → should produce 502
 * new FaultyLlmClient(FaultMode.EMPTY_TITLE)
 *
 * // Returns impossible date → should produce 502
 * new FaultyLlmClient(FaultMode.IMPOSSIBLE_DATE)
 *
 * // Throws BadGatewayException → should produce 502
 * new FaultyLlmClient(FaultMode.NETWORK_ERROR)
 *
 * // Returns malformed JSON → should produce 502
 * new FaultyLlmClient(FaultMode.MALFORMED_JSON)
 * </pre>
 */
public final class FaultyLlmClient extends LlmClient {

    public enum FaultMode {
        /** Returns JSON with an empty title — validation must catch this. */
        EMPTY_TITLE,
        /** Returns JSON with an impossible date (Feb 30). */
        IMPOSSIBLE_DATE,
        /** Throws BadGatewayException simulating a network failure. */
        NETWORK_ERROR,
        /** Throws ServiceUnavailableException simulating a missing API key. */
        NO_API_KEY,
        /** Returns a string that is not JSON at all. */
        MALFORMED_JSON,
        /** Returns JSON where both title and description are blank. */
        BLANK_CONTENT
    }

    private final FaultMode mode;

    public FaultyLlmClient(FaultMode mode) {
        this.mode = mode;
    }

    @Override
    public String complete(String prompt) {
        return switch (mode) {
            case EMPTY_TITLE ->
                "{ \"title\": \"\", \"description\": \"Some description\", " +
                "\"dueDate\": null, \"recurrence\": null }";

            case IMPOSSIBLE_DATE ->
                "{ \"title\": \"Pay rent\", \"description\": \"Monthly payment\", " +
                "\"dueDate\": \"2026-02-30\", \"recurrence\": \"monthly\" }";

            case NETWORK_ERROR ->
                throw new BadGatewayException("Simulated network timeout");

            case NO_API_KEY ->
                throw new ServiceUnavailableException(
                        "LLM_API_KEY environment variable is not set.");

            case MALFORMED_JSON ->
                "this is not json at all !!!";

            case BLANK_CONTENT ->
                "{ \"title\": \"   \", \"description\": \"   \", " +
                "\"dueDate\": null, \"recurrence\": null }";
        };
    }
}
