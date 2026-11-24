package com.taskmanager.parser;

/**
 * Factory that returns the appropriate TaskParser based on runtime configuration.
 *
 * Strategy selection:
 *   - If {@code LLM_API_KEY} is set in the environment → returns LlmTaskParser.
 *   - Otherwise → returns RuleBasedTaskParser for offline / test use.
 *
 * The service layer never calls this factory directly after wiring; it only
 * holds a TaskParser reference, keeping it decoupled from the selection logic.
 */
public final class ParserFactory {

    // Utility class — no instances
    private ParserFactory() {}

    /**
     * Returns the best available TaskParser for the current environment.
     * Pass the result into TaskService via its constructor.
     */
    public static TaskParser create() {
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            return new LlmTaskParser(new LlmClient());
        }
        return new RuleBasedTaskParser();
    }

    /**
     * Always returns an LlmTaskParser with the supplied client.
     * Useful for injecting a fake client in tests.
     */
    public static TaskParser llm(LlmClient client) {
        return new LlmTaskParser(client);
    }

    /**
     * Always returns a RuleBasedTaskParser, regardless of environment.
     */
    public static TaskParser ruleBased() {
        return new RuleBasedTaskParser();
    }
}
