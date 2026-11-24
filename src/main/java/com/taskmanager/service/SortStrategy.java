package com.taskmanager.service;

import com.taskmanager.model.Task;

import java.util.Comparator;
import java.util.List;

/**
 * Open/Closed sort strategy: add new orderings without editing existing code.
 *
 * <p>Each implementation sorts the task list in-place via a {@link Comparator}.
 * Multiple strategies can be chained with {@link Chained}.</p>
 */
public interface SortStrategy {

    /**
     * Sorts {@code tasks} in-place according to this strategy.
     */
    void sort(List<Task> tasks);

    // ── Built-in implementations ──────────────────────────────────────────────

    /** Sorts by due date ascending; tasks with no due date go last. */
    final class ByDueDateAsc implements SortStrategy {
        @Override
        public void sort(List<Task> tasks) {
            tasks.sort(Comparator.comparing(Task::getDueDate,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }
    }

    /** Sorts by due date descending; tasks with no due date go last. */
    final class ByDueDateDesc implements SortStrategy {
        @Override
        public void sort(List<Task> tasks) {
            tasks.sort(Comparator.comparing(Task::getDueDate,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }
    }

    /**
     * Sorts by priority descending (HIGH → MEDIUM → LOW).
     * Uses the natural enum ordinal reversed.
     */
    final class ByPriorityDesc implements SortStrategy {
        @Override
        public void sort(List<Task> tasks) {
            tasks.sort(Comparator.comparing(Task::getPriority).reversed());
        }
    }

    /** Sorts by priority ascending (LOW → MEDIUM → HIGH). */
    final class ByPriorityAsc implements SortStrategy {
        @Override
        public void sort(List<Task> tasks) {
            tasks.sort(Comparator.comparing(Task::getPriority));
        }
    }

    /** Sorts alphabetically by title (case-insensitive, ascending). */
    final class ByTitleAsc implements SortStrategy {
        @Override
        public void sort(List<Task> tasks) {
            tasks.sort(Comparator.comparing(
                    t -> t.getTitle().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    /** Sorts alphabetically by title descending. */
    final class ByTitleDesc implements SortStrategy {
        @Override
        public void sort(List<Task> tasks) {
            tasks.sort(Comparator.comparing(
                    (Task t) -> t.getTitle().toLowerCase(java.util.Locale.ROOT)).reversed());
        }
    }

    /**
     * Chains two strategies: primary first, then secondary as a tiebreaker.
     * Example: sort by priority first, then by title for tasks of equal priority.
     */
    final class Chained implements SortStrategy {
        private final SortStrategy primary;
        private final SortStrategy secondary;

        public Chained(SortStrategy primary, SortStrategy secondary) {
            this.primary   = primary;
            this.secondary = secondary;
        }

        @Override
        public void sort(List<Task> tasks) {
            // Build combined comparator
            tasks.sort((a, b) -> {
                List<Task> pairA = new java.util.ArrayList<>(List.of(a, b));
                primary.sort(pairA);
                if (pairA.get(0) != a) return 1;   // primary says b < a
                // Check if primary considers them equal by sorting reversed
                List<Task> pairB = new java.util.ArrayList<>(List.of(b, a));
                primary.sort(pairB);
                if (pairB.get(0) != b) return 0;   // equal under primary
                // Apply secondary as tiebreaker
                List<Task> pair = new java.util.ArrayList<>(List.of(a, b));
                secondary.sort(pair);
                return pair.get(0) == a ? -1 : 1;
            });
        }
    }
}
