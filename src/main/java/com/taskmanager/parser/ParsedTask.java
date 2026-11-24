package com.taskmanager.parser;

/**
 * Holds the structured data extracted from a plain-English task description.
 * Produced by any TaskParser implementation and consumed by TaskService.
 */
public final class ParsedTask {

    private final String title;
    private final String description;
    private final String dueDate;      // YYYY-MM-DD or null
    private final String recurrence;   // e.g. "monthly", "weekly" or null

    private ParsedTask(Builder builder) {
        this.title      = builder.title;
        this.description = builder.description;
        this.dueDate    = builder.dueDate;
        this.recurrence = builder.recurrence;
    }

    public String getTitle()       { return title; }
    public String getDescription() { return description; }
    public String getDueDate()     { return dueDate; }
    public String getRecurrence()  { return recurrence; }

    @Override
    public String toString() {
        return "ParsedTask{title='" + title + "', description='" + description +
               "', dueDate='" + dueDate + "', recurrence='" + recurrence + "'}";
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private String title;
        private String description;
        private String dueDate;
        private String recurrence;

        public Builder title(String title)             { this.title = title;           return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder dueDate(String dueDate)         { this.dueDate = dueDate;       return this; }
        public Builder recurrence(String recurrence)   { this.recurrence = recurrence; return this; }

        public ParsedTask build() { return new ParsedTask(this); }
    }
}
