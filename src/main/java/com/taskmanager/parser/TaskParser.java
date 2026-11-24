package com.taskmanager.parser;

/**
 * Strategy interface for converting plain-English text into a ParsedTask.
 *
 * <p>Any implementation must be plug-and-play: TaskService depends only on
 * this interface (Dependency Inversion Principle), so callers can supply an
 * LLM-backed parser, a rule-based parser, or a test double without touching
 * service code.</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@code text} – the raw user input, e.g. "Remind me to pay rent on the 1st of every month"</li>
 *   <li>{@code today} – ISO date of today (YYYY-MM-DD) so relative phrases like
 *       "next Friday" or "tomorrow" can be resolved deterministically.</li>
 *   <li>Returns a {@link ParsedTask} with at least title and description populated.</li>
 *   <li>Throws {@link com.taskmanager.exception.BadGatewayException} if an upstream
 *       call fails (LLM unreachable, timeout, bad response).</li>
 *   <li>Throws {@link com.taskmanager.exception.ServiceUnavailableException} if the
 *       required API key is absent.</li>
 * </ul>
 *
 * <h3>Adding a new parser</h3>
 * <ol>
 *   <li>Implement this interface.</li>
 *   <li>Register it in {@link ParserFactory} (or inject it directly).</li>
 *   <li>No changes to TaskService or TaskApi are required.</li>
 * </ol>
 */
public interface TaskParser {

    /**
     * Parses the plain-English {@code text} into a structured {@link ParsedTask}.
     *
     * @param text  raw user input (never null or blank)
     * @param today today's date in YYYY-MM-DD format, used to resolve relative dates
     * @return a populated {@link ParsedTask}
     */
    ParsedTask parse(String text, String today);
}
