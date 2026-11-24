package com.taskmanager.parser;

import com.taskmanager.exception.BadGatewayException;
import com.taskmanager.exception.ServiceUnavailableException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Thin HTTP client that sends a prompt to an LLM provider and returns
 * the raw text reply.
 *
 * <p>Supported provider is Anthropic (Claude). The model and endpoint are
 * chosen automatically based on the environment variable present:</p>
 * <ul>
 *   <li>{@code LLM_API_KEY} → Anthropic Claude claude-3-haiku-20240307</li>
 * </ul>
 *
 * <p>The API key is read from the environment at call time — never stored
 * in source code.</p>
 */
public class LlmClient {

    private static final String ANTHROPIC_URL   = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_MODEL = "claude-3-haiku-20240307";
    private static final int    TIMEOUT_MS      = 30_000;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends {@code prompt} to the configured LLM and returns the response text.
     *
     * @throws ServiceUnavailableException if no API key is configured
     * @throws BadGatewayException         if the HTTP call fails or returns a non-200 status
     */
    public String complete(String prompt) {
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceUnavailableException(
                    "LLM_API_KEY environment variable is not set. " +
                    "Set it to your Anthropic API key to enable AI features.");
        }
        return callAnthropic(apiKey, prompt);
    }

    // ── Anthropic ─────────────────────────────────────────────────────────────

    private String callAnthropic(String apiKey, String prompt) {
        String requestBody = buildAnthropicBody(prompt);

        try {
            HttpURLConnection conn = openConnection(ANTHROPIC_URL);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                String err = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new BadGatewayException(
                        "Anthropic API returned HTTP " + status + ": " + truncate(err, 200));
            }

            String responseJson = new String(
                    conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return extractAnthropicText(responseJson);

        } catch (BadGatewayException | ServiceUnavailableException e) {
            throw e;
        } catch (IOException e) {
            throw new BadGatewayException("LLM network error: " + e.getMessage());
        }
    }

    private String buildAnthropicBody(String prompt) {
        // Hand-built JSON — no external library needed
        String escaped = jsonEscape(prompt);
        return "{\n" +
               "  \"model\": \"" + ANTHROPIC_MODEL + "\",\n" +
               "  \"max_tokens\": 1024,\n" +
               "  \"messages\": [\n" +
               "    { \"role\": \"user\", \"content\": \"" + escaped + "\" }\n" +
               "  ]\n" +
               "}";
    }

    /**
     * Extracts the text from Anthropic's response JSON.
     * Shape: {"content":[{"type":"text","text":"..."}],...}
     */
    private String extractAnthropicText(String json) {
        // Look for "text": "..."  — works for single-block replies
        int textIdx = json.indexOf("\"text\":");
        if (textIdx < 0) {
            throw new BadGatewayException("Unexpected LLM response format: " + truncate(json, 200));
        }
        int start = json.indexOf('"', textIdx + 7) + 1;
        int end   = findUnescapedQuote(json, start);
        if (start <= 0 || end <= start) {
            throw new BadGatewayException("Could not parse LLM response: " + truncate(json, 200));
        }
        return unescapeJson(json.substring(start, end));
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    /** Opens an HttpURLConnection from a URL string. Package-private for testing overrides. */
    HttpURLConnection openConnection(String url) throws IOException {
        return (HttpURLConnection) URI.create(url).toURL().openConnection();
    }

    /** Finds the index of the next unescaped double-quote at or after {@code from}. */
    private static int findUnescapedQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) return i;
        }
        return -1;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
