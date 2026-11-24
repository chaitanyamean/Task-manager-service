package com.taskmanager.service;

import com.taskmanager.model.TaskRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline BreakdownStrategy that splits a task description using simple rules.
 *
 * Split strategy (in order of precedence):
 *   1. Split on " and " — "Buy milk and eggs and bread" → 3 subtasks.
 *   2. Split on commas  — "Draft, review, publish the report" → 3 subtasks.
 *   3. Fall back to a single subtask equal to the parent.
 *
 * Returns at least one subtask, so TaskService always has something to validate.
 */
public final class RuleBasedBreakdownStrategy implements BreakdownStrategy {

    @Override
    public List<TaskRequest> breakdown(String title, String description) {
        List<String> parts = split(description);

        List<TaskRequest> requests = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            if (part.isEmpty()) continue;

            // Capitalise first letter
            String subtaskTitle = Character.toUpperCase(part.charAt(0)) + part.substring(1);
            String subtaskDesc  = "Subtask " + (i + 1) + " of: " + title;

            requests.add(new TaskRequest.Builder()
                    .title(subtaskTitle)
                    .description(subtaskDesc)
                    .completed("false")
                    .build());
        }

        // Fallback: at least one subtask
        if (requests.isEmpty()) {
            requests.add(new TaskRequest.Builder()
                    .title(title)
                    .description(description)
                    .completed("false")
                    .build());
        }

        return requests;
    }

    // ── Splitting logic ───────────────────────────────────────────────────────

    private List<String> split(String text) {
        String lower = text.toLowerCase(Locale.ROOT);

        // Prefer splitting on " and "
        if (lower.contains(" and ")) {
            return listOf(text.split("(?i)\\s+and\\s+"));
        }

        // Fall back to comma split
        if (text.contains(",")) {
            return listOf(text.split(","));
        }

        // No separator found — single item
        return List.of(text);
    }

    private static List<String> listOf(String[] arr) {
        List<String> list = new ArrayList<>();
        for (String s : arr) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) list.add(trimmed);
        }
        return list;
    }
}
