package com.taskmanager.parser;

/**
 * Minimal JSON reader for flat objects (no nesting, no arrays needed here).
 *
 * <p>Handles the shape returned by the LLM prompts in this project:
 * <pre>
 * {
 *   "title": "Pay rent",
 *   "description": "Transfer on the 1st",
 *   "dueDate": "2026-12-01",
 *   "recurrence": "monthly"
 * }
 * </pre>
 * </p>
 *
 * <p>No external library is used — values are located with {@code indexOf}
 * and extracted between quote pairs.</p>
 */
public final class SimpleJson {

    private final String json;

    public SimpleJson(String json) {
        this.json = json == null ? "{}" : json.trim();
    }

    /**
     * Returns the string value for {@code key}, or {@code null} if the key
     * is absent, the value is JSON {@code null}, or the value is an empty string.
     */
    public String getString(String key) {
        // Find  "key":
        String needle = "\"" + key + "\"";
        int keyIdx = json.indexOf(needle);
        if (keyIdx < 0) return null;

        // Skip past the key and optional whitespace / colon
        int colonIdx = json.indexOf(':', keyIdx + needle.length());
        if (colonIdx < 0) return null;

        // Find first non-whitespace character after the colon
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;

        // null literal
        if (json.startsWith("null", valueStart)) return null;

        // String value enclosed in double-quotes
        if (json.charAt(valueStart) == '"') {
            int contentStart = valueStart + 1;
            int contentEnd   = findUnescapedQuote(json, contentStart);
            if (contentEnd < 0) return null;
            String raw = json.substring(contentStart, contentEnd);
            String val = unescapeJson(raw);
            return val.isBlank() ? null : val;
        }

        return null;
    }

    /**
     * Returns {@code true} if the JSON object contains the given key with a non-null value.
     */
    public boolean has(String key) {
        return getString(key) != null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int findUnescapedQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) return i;
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
