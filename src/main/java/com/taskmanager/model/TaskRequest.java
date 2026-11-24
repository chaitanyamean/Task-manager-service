package com.taskmanager.model;

/**
 * Raw input carrier for task operations.
 * All fields are plain Strings — validation happens in TaskService.
 */
public final class TaskRequest {

    private final String title;
    private final String description;
    private final String completed;   // "true" or "false" – validated later
    private final String dueDate;     // "YYYY-MM-DD" string – validated later
    private final String priority;    // "LOW" / "MEDIUM" / "HIGH" – validated later
    private final String parentTaskId;    // optional
    private final String idempotencyKey;  // optional

    private TaskRequest(Builder builder) {
        this.title          = builder.title;
        this.description    = builder.description;
        this.completed      = builder.completed;
        this.dueDate        = builder.dueDate;
        this.priority       = builder.priority;
        this.parentTaskId   = builder.parentTaskId;
        this.idempotencyKey = builder.idempotencyKey;
    }

    public String getTitle()          { return title; }
    public String getDescription()    { return description; }
    public String getCompleted()      { return completed; }
    public String getDueDate()        { return dueDate; }
    public String getPriority()       { return priority; }
    public String getParentTaskId()   { return parentTaskId; }
    public String getIdempotencyKey() { return idempotencyKey; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private String title;
        private String description;
        private String completed;
        private String dueDate;
        private String priority;
        private String parentTaskId;
        private String idempotencyKey;

        public Builder title(String title)                 { this.title = title;               return this; }
        public Builder description(String description)     { this.description = description;   return this; }
        public Builder completed(String completed)         { this.completed = completed;       return this; }
        public Builder dueDate(String dueDate)             { this.dueDate = dueDate;           return this; }
        public Builder priority(String priority)           { this.priority = priority;         return this; }
        public Builder parentTaskId(String parentTaskId)   { this.parentTaskId = parentTaskId; return this; }
        public Builder idempotencyKey(String key)          { this.idempotencyKey = key;        return this; }

        public TaskRequest build() { return new TaskRequest(this); }
    }
}
