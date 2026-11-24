package com.taskmanager.parser;

/**
 * Test double for LlmClient that returns a fixed, well-formed JSON reply.
 * No network call, no API key required.
 *
 * Usage:
 *   TaskParser parser = ParserFactory.llm(new FakeLlmClient());
 */
public final class FakeLlmClient extends LlmClient {

    private final String fixedReply;

    /**
     * Creates a fake that always returns the given JSON string as the LLM reply.
     */
    public FakeLlmClient(String fixedReply) {
        this.fixedReply = fixedReply;
    }

    /** Convenience: returns a valid task JSON with the given title. */
    public static FakeLlmClient withTitle(String title) {
        return new FakeLlmClient(
                "{ \"title\": \"" + title + "\", " +
                "\"description\": \"Auto-generated task\", " +
                "\"dueDate\": null, \"recurrence\": null }");
    }

    /** Convenience: returns a valid task JSON with a due date. */
    public static FakeLlmClient withDueDate(String title, String dueDate) {
        return new FakeLlmClient(
                "{ \"title\": \"" + title + "\", " +
                "\"description\": \"Auto-generated task\", " +
                "\"dueDate\": \"" + dueDate + "\", \"recurrence\": null }");
    }

    @Override
    public String complete(String prompt) {
        return fixedReply;
    }
}
