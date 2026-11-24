package com.taskmanager.parser;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Offline TaskParser that uses String manipulation instead of an LLM.
 *
 * Handles common natural-language patterns:
 *   - "tomorrow"
 *   - "next Monday/Tuesday/…/Sunday"
 *   - "on the Nth" or "every Nth" → monthly recurrence
 *   - "every day / daily"
 *   - "every week / weekly"
 *   - "every month / monthly"
 *   - "every year / yearly / annually"
 *
 * Falls back gracefully when no pattern is recognised:
 *   - title  = first sentence (up to '.' or 50 chars)
 *   - description = full input text
 */
public final class RuleBasedTaskParser implements TaskParser {

    @Override
    public ParsedTask parse(String text, String today) {
        String lower = text.toLowerCase(Locale.ROOT).trim();

        String dueDate    = resolveDueDate(lower, today);
        String recurrence = resolveRecurrence(lower);
        String title      = extractTitle(text);

        return new ParsedTask.Builder()
                .title(title)
                .description(text.trim())
                .dueDate(dueDate)
                .recurrence(recurrence)
                .build();
    }

    // ── Due-date resolution ───────────────────────────────────────────────────

    private String resolveDueDate(String lower, String today) {
        LocalDate base = LocalDate.parse(today);

        if (lower.contains("tomorrow")) {
            return base.plusDays(1).toString();
        }

        // "next <weekday>"
        String[] weekdays = {"monday","tuesday","wednesday","thursday","friday","saturday","sunday"};
        for (int i = 0; i < weekdays.length; i++) {
            if (lower.contains("next " + weekdays[i])) {
                // ISO day-of-week: Monday=1 … Sunday=7
                int targetDow = i + 1;
                int currentDow = base.getDayOfWeek().getValue();
                int daysAhead = targetDow - currentDow;
                if (daysAhead <= 0) daysAhead += 7;
                return base.plusDays(daysAhead).toString();
            }
        }

        // "on the Nth" or "every Nth" → resolve to next occurrence of that day-of-month
        int dayOfMonth = extractDayOfMonth(lower);
        if (dayOfMonth > 0) {
            LocalDate candidate = base.withDayOfMonth(Math.min(dayOfMonth, base.lengthOfMonth()));
            if (!candidate.isAfter(base)) {
                // already passed this month — move to next month
                LocalDate next = base.plusMonths(1);
                candidate = next.withDayOfMonth(Math.min(dayOfMonth, next.lengthOfMonth()));
            }
            return candidate.toString();
        }

        return null;
    }

    /** Extracts an ordinal day-of-month number: "1st", "2nd", "3rd", "15th", etc. */
    private int extractDayOfMonth(String lower) {
        // Match patterns like "1st", "2nd", "3rd", "4th" … "31st"
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)").matcher(lower);
        if (m.find()) {
            int day = Integer.parseInt(m.group(1));
            if (day >= 1 && day <= 31) return day;
        }
        return -1;
    }

    // ── Recurrence resolution ─────────────────────────────────────────────────

    private String resolveRecurrence(String lower) {
        if (lower.contains("every day") || lower.contains("daily"))               return "daily";
        if (lower.contains("every week") || lower.contains("weekly"))             return "weekly";
        if (lower.contains("every month") || lower.contains("monthly")
                || lower.contains("every ") && extractDayOfMonth(lower) > 0)     return "monthly";
        if (lower.contains("every year") || lower.contains("yearly")
                || lower.contains("annually"))                                    return "yearly";
        return null;
    }

    // ── Title extraction ──────────────────────────────────────────────────────

    /**
     * Returns the first sentence of {@code text} (up to the first '.' or 50 chars),
     * stripped of common filler phrases.
     */
    private String extractTitle(String text) {
        String stripped = text.trim();

        // Remove leading filler phrases
        String[] fillers = {
            "remind me to ", "remind me ", "i need to ", "i have to ",
            "please ", "make sure to ", "don't forget to "
        };
        String lower = stripped.toLowerCase(Locale.ROOT);
        for (String filler : fillers) {
            if (lower.startsWith(filler)) {
                stripped = stripped.substring(filler.length());
                break;
            }
        }

        // Take up to first full stop
        int dot = stripped.indexOf('.');
        if (dot > 0 && dot <= 60) stripped = stripped.substring(0, dot);

        // Cap length
        if (stripped.length() > 60) stripped = stripped.substring(0, 60).trim();

        // Capitalise first letter
        if (!stripped.isEmpty()) {
            stripped = Character.toUpperCase(stripped.charAt(0)) + stripped.substring(1);
        }

        return stripped;
    }
}
