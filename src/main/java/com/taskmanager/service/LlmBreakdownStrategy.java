package com.taskmanager.service;

import com.taskmanager.exception.BadGatewayException;
import com.taskmanager.model.TaskRequest;
import com.taskmanager.parser.LlmClient;

import java.util.ArrayList;
import java.util.List;

/**
 * BreakdownStrategy that asks an LLM to propose subtasks.
 *
 * <p>The model is instructed to reply with one subtask per line in the format:
 * <pre>title|description</pre>
 * This fixed format is parsed with String methods — no JSON library needed.</p>
 *
 * <p>"Never trust the model" rule: every subtask request returned here is
 * validated by TaskService.validate() before anything is persisted.</p>
 */
public final class LlmBreakdownStrategy implements BreakdownStrategy {

    private final LlmClient llmClient;

    public LlmBreakdownStrategy(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public List<TaskRequest> breakdown(String title, String description) {
        String prompt  = buildPrompt(title, description);
        String reply   = llmClient.complete(prompt);   // throws ServiceUnavailable / BadGateway
        return parseReply(reply);
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private String buildPrompt(String title, String description) {
        return "Break down the following task into 3-5 concrete subtasks.\n" +
               "Reply with ONLY the subtasks — one per line, no numbering, no extra text.\n" +
               "Each line must follow EXACTLY this format:  title|description\n\n" +
               "Task title: " + title + "\n" +
               "Task description: " + description + "\n\n" +
               "Subtasks:";
    }

    // ── Reply parsing ─────────────────────────────────────────────────────────

    private List<TaskRequest> parseReply(String reply) {
        List<TaskRequest> requests = new ArrayList<>();

        for (String rawLine : reply.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Strip leading list markers the model might add (-, *, 1., etc.)
            line = line.replaceFirst("^[-*•\\d]+[.)\\s]+", "").trim();
            if (line.isEmpty()) continue;

            int pipe = line.indexOf('|');
            if (pipe < 0) {
                // No pipe — treat the whole line as the title, derive description
                String t = truncate(line, 100);
                requests.add(new TaskRequest.Builder()
                        .title(t)
                        .description(t)
                        .completed("false")
                        .build());
            } else {
                String subtaskTitle = line.substring(0, pipe).trim();
                String subtaskDesc  = line.substring(pipe + 1).trim();
                if (subtaskTitle.isEmpty()) subtaskTitle = subtaskDesc;
                if (subtaskDesc.isEmpty())  subtaskDesc  = subtaskTitle;
                requests.add(new TaskRequest.Builder()
                        .title(truncate(subtaskTitle, 100))
                        .description(truncate(subtaskDesc, 500))
                        .completed("false")
                        .build());
            }
        }

        if (requests.isEmpty()) {
            throw new BadGatewayException("LLM returned no subtasks for the breakdown request.");
        }

        return requests;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max).trim();
    }
}
