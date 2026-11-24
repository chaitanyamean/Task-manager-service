package com.taskmanager.parser;

import com.taskmanager.exception.BadGatewayException;

/**
 * TaskParser implementation that delegates to an LLM.
 *
 * Strategy pattern: TaskService depends only on the TaskParser interface,
 * so this class can be swapped for any other implementation without changes
 * to service code.
 *
 * Prompt contract:
 *   - Supplies today's date so relative phrases ("next Friday") resolve correctly.
 *   - Instructs the model to reply with ONLY a JSON object — no prose.
 *   - Expected shape:
 *     {
 *       "title":       "...",
 *       "description": "...",
 *       "dueDate":     "YYYY-MM-DD" or null,
 *       "recurrence":  "daily|weekly|monthly|yearly" or null
 *     }
 *
 * "Never trust the model" rule:
 *   - The returned ParsedTask is always fed through TaskService.validate()
 *     before a Task is persisted.
 */
public final class LlmTaskParser implements TaskParser {

    private final LlmClient llmClient;

    public LlmTaskParser(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public ParsedTask parse(String text, String today) {
        String prompt = buildPrompt(text, today);
        String rawReply = llmClient.complete(prompt);   // throws ServiceUnavailableException / BadGatewayException
        return parseReply(rawReply, text);
    }

    // ── Prompt construction ───────────────────────────────────────────────────

    private String buildPrompt(String text, String today) {
        return "Today's date is " + today + ".\n\n" +
               "Convert the following plain-English task description into a JSON object.\n" +
               "Reply with ONLY the JSON object — no explanation, no markdown, no code fences.\n\n" +
               "Required fields:\n" +
               "  \"title\"       – a short, imperative task title (non-empty string)\n" +
               "  \"description\" – one sentence explaining what needs to be done (non-empty string)\n\n" +
               "Optional fields (use JSON null if not applicable):\n" +
               "  \"dueDate\"    – the resolved due date in YYYY-MM-DD format, or null\n" +
               "  \"recurrence\" – one of: daily, weekly, monthly, yearly, or null\n\n" +
               "Task description:\n" +
               "\"" + text + "\"\n\n" +
               "JSON:";
    }

    // ── Reply parsing ─────────────────────────────────────────────────────────

    private ParsedTask parseReply(String rawReply, String originalText) {
        // Strip any accidental markdown code fences the model may have added
        String json = stripCodeFences(rawReply.trim());

        SimpleJson sj = new SimpleJson(json);

        String title       = sj.getString("title");
        String description = sj.getString("description");
        String dueDate     = sj.getString("dueDate");
        String recurrence  = sj.getString("recurrence");

        // If the model returned nothing useful, surface a clear error
        if ((title == null || title.isBlank()) && (description == null || description.isBlank())) {
            throw new BadGatewayException(
                    "LLM returned an unusable response for input: \"" + truncate(originalText, 80) + "\"");
        }

        // If only one of the two is missing, do NOT silently substitute.
        // Let TaskService.validate() catch it and raise 502 (never trust the model).

        return new ParsedTask.Builder()
                .title(title)
                .description(description)
                .dueDate(dueDate)
                .recurrence(recurrence)
                .build();
    }

    /** Removes ```json ... ``` or ``` ... ``` fences if the model added them. */
    private static String stripCodeFences(String s) {
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) s = s.substring(firstNewline + 1);
            if (s.endsWith("```")) s = s.substring(0, s.lastIndexOf("```")).trim();
        }
        return s;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
