package com.taskmanager.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable Task model. All fields are private final.
 * Objects are created only through the inner Builder.
 */
public final class Task {

    private final String id;
    private final String title;
    private final String description;
    private final boolean completed;
    private final LocalDate dueDate;      // optional
    private final Priority priority;      // optional, defaults to MEDIUM
    private final String parentTaskId;    // optional – for subtasks
    private final List<String> subtaskIds; // IDs of child tasks
    private final String idempotencyKey;  // optional – for safe retries

    // Private constructor: only Builder creates instances
    private Task(Builder builder) {
        this.id             = builder.id;
        this.title          = builder.title;
        this.description    = builder.description;
        this.completed      = builder.completed;
        this.dueDate        = builder.dueDate;
        this.priority       = builder.priority;
        this.parentTaskId   = builder.parentTaskId;
        this.subtaskIds     = Collections.unmodifiableList(new ArrayList<>(builder.subtaskIds));
        this.idempotencyKey = builder.idempotencyKey;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getId()             { return id; }
    public String getTitle()          { return title; }
    public String getDescription()    { return description; }
    public boolean isCompleted()      { return completed; }
    public LocalDate getDueDate()     { return dueDate; }
    public Priority getPriority()     { return priority; }
    public String getParentTaskId()   { return parentTaskId; }
    public List<String> getSubtaskIds() { return subtaskIds; }
    public String getIdempotencyKey() { return idempotencyKey; }

    /**
     * Returns a new Builder pre-populated with this task's values,
     * useful for creating an updated copy (since Task is immutable).
     */
    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .title(this.title)
                .description(this.description)
                .completed(this.completed)
                .dueDate(this.dueDate)
                .priority(this.priority)
                .parentTaskId(this.parentTaskId)
                .subtaskIds(new ArrayList<>(this.subtaskIds))
                .idempotencyKey(this.idempotencyKey);
    }

    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", completed=" + completed +
                ", dueDate=" + dueDate +
                ", priority=" + priority +
                ", parentTaskId='" + parentTaskId + '\'' +
                ", subtaskIds=" + subtaskIds +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                '}';
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {

        // Required fields (validated by TaskService, not here)
        private String id = UUID.randomUUID().toString();
        private String title;
        private String description;
        private boolean completed = false;

        // Optional fields
        private LocalDate dueDate        = null;
        private Priority  priority       = Priority.MEDIUM;
        private String    parentTaskId   = null;
        private List<String> subtaskIds  = new ArrayList<>();
        private String    idempotencyKey = null;

        public Builder id(String id)                       { this.id = id;                     return this; }
        public Builder title(String title)                 { this.title = title;               return this; }
        public Builder description(String description)     { this.description = description;   return this; }
        public Builder completed(boolean completed)        { this.completed = completed;       return this; }
        public Builder dueDate(LocalDate dueDate)          { this.dueDate = dueDate;           return this; }
        public Builder priority(Priority priority)         { this.priority = priority;         return this; }
        public Builder parentTaskId(String parentTaskId)   { this.parentTaskId = parentTaskId; return this; }
        public Builder subtaskIds(List<String> ids)        { this.subtaskIds = ids;            return this; }
        public Builder idempotencyKey(String key)          { this.idempotencyKey = key;        return this; }

        /** Adds a single subtask ID without replacing the whole list. */
        public Builder addSubtaskId(String subtaskId) {
            this.subtaskIds.add(subtaskId);
            return this;
        }

        public Task build() {
            return new Task(this);
        }
    }
}
