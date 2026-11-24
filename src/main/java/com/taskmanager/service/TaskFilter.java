package com.taskmanager.service;

import com.taskmanager.model.Task;

/**
 * Open/Closed filter strategy: add new filters without editing existing code.
 *
 * <p>Each implementation tests one condition against a Task.
 * Filters are composited in {@link TaskQuery} — a task passes when
 * every registered filter returns true.</p>
 */
public interface TaskFilter {

    /**
     * Returns {@code true} if {@code task} satisfies this filter condition.
     */
    boolean matches(Task task);

    // ── Built-in implementations ──────────────────────────────────────────────

    /** Keeps only tasks with the given completed status. */
    final class ByCompleted implements TaskFilter {
        private final boolean completed;
        public ByCompleted(boolean completed) { this.completed = completed; }

        @Override
        public boolean matches(Task task) { return task.isCompleted() == completed; }
    }

    /** Keeps only tasks whose priority matches. */
    final class ByPriority implements TaskFilter {
        private final com.taskmanager.model.Priority priority;
        public ByPriority(com.taskmanager.model.Priority priority) { this.priority = priority; }

        @Override
        public boolean matches(Task task) { return task.getPriority() == priority; }
    }

    /**
     * Full-text keyword search: matches if the keyword appears (case-insensitive)
     * in the title OR description.
     */
    final class ByKeyword implements TaskFilter {
        private final String keyword;
        public ByKeyword(String keyword) { this.keyword = keyword.toLowerCase(java.util.Locale.ROOT); }

        @Override
        public boolean matches(Task task) {
            String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase(java.util.Locale.ROOT);
            String desc  = task.getDescription() == null ? "" : task.getDescription().toLowerCase(java.util.Locale.ROOT);
            return title.contains(keyword) || desc.contains(keyword);
        }
    }

    /** Keeps only top-level tasks (tasks without a parent). */
    final class TopLevelOnly implements TaskFilter {
        @Override
        public boolean matches(Task task) {
            return task.getParentTaskId() == null || task.getParentTaskId().isBlank();
        }
    }

    /** Keeps only subtasks (tasks that have a parent). */
    final class SubtasksOnly implements TaskFilter {
        @Override
        public boolean matches(Task task) {
            return task.getParentTaskId() != null && !task.getParentTaskId().isBlank();
        }
    }

    /** Keeps only tasks whose dueDate is on or before the given date. */
    final class DueBefore implements TaskFilter {
        private final java.time.LocalDate cutoff;
        public DueBefore(java.time.LocalDate cutoff) { this.cutoff = cutoff; }

        @Override
        public boolean matches(Task task) {
            return task.getDueDate() != null && !task.getDueDate().isAfter(cutoff);
        }
    }
}
