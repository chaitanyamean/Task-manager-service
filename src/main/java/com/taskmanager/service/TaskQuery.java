package com.taskmanager.service;

import com.taskmanager.model.Priority;
import com.taskmanager.model.Task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Composable query object built with the Builder pattern.
 *
 * <p>All fields are optional — an empty query returns every task.
 * Filters, sort, and pagination all compose cleanly in one call:</p>
 *
 * <pre>
 * List&lt;Task&gt; results = new TaskQuery.Builder()
 *     .filterByCompleted(false)
 *     .filterByPriority(Priority.HIGH)
 *     .filterByKeyword("rent")
 *     .sortBy(new SortStrategy.ByDueDateAsc())
 *     .page(1, 10)
 *     .build()
 *     .execute(allTasks);
 * </pre>
 *
 * <p>Adding a new filter or sort never touches this class — just implement
 * {@link TaskFilter} or {@link SortStrategy} and pass it in (Open/Closed).</p>
 */
public final class TaskQuery {

    private final List<TaskFilter> filters;
    private final SortStrategy     sortStrategy;   // null = keep insertion order
    private final int              page;            // 1-based; ≤0 = no pagination
    private final int              pageSize;        // ≤0 = no pagination

    private TaskQuery(Builder builder) {
        this.filters      = List.copyOf(builder.filters);
        this.sortStrategy = builder.sortStrategy;
        this.page         = builder.page;
        this.pageSize     = builder.pageSize;
    }

    /**
     * Applies all filters, then sort, then pagination to {@code source}.
     * Does not modify {@code source}.
     */
    public List<Task> execute(List<Task> source) {
        // 1. Filter
        List<Task> result = source.stream()
                .filter(task -> filters.stream().allMatch(f -> f.matches(task)))
                .collect(Collectors.toList());

        // 2. Sort
        if (sortStrategy != null) {
            sortStrategy.sort(result);
        }

        // 3. Paginate
        if (page > 0 && pageSize > 0) {
            int from = (page - 1) * pageSize;
            if (from >= result.size()) return List.of();
            result = result.subList(from, Math.min(from + pageSize, result.size()));
        }

        return result;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {

        private final List<TaskFilter> filters      = new ArrayList<>();
        private SortStrategy           sortStrategy = null;
        private int                    page         = 0;
        private int                    pageSize     = 0;

        // ── Filter helpers ────────────────────────────────────────────────────

        public Builder filterByCompleted(boolean completed) {
            filters.add(new TaskFilter.ByCompleted(completed));
            return this;
        }

        public Builder filterByPriority(Priority priority) {
            filters.add(new TaskFilter.ByPriority(priority));
            return this;
        }

        public Builder filterByKeyword(String keyword) {
            if (keyword != null && !keyword.isBlank())
                filters.add(new TaskFilter.ByKeyword(keyword));
            return this;
        }

        public Builder topLevelOnly() {
            filters.add(new TaskFilter.TopLevelOnly());
            return this;
        }

        public Builder subtasksOnly() {
            filters.add(new TaskFilter.SubtasksOnly());
            return this;
        }

        public Builder dueBefore(LocalDate cutoff) {
            filters.add(new TaskFilter.DueBefore(cutoff));
            return this;
        }

        /** Plug in any custom {@link TaskFilter} — Open/Closed in action. */
        public Builder addFilter(TaskFilter filter) {
            filters.add(filter);
            return this;
        }

        // ── Sort helpers ──────────────────────────────────────────────────────

        public Builder sortByDueDateAsc()   { sortStrategy = new SortStrategy.ByDueDateAsc();   return this; }
        public Builder sortByDueDateDesc()  { sortStrategy = new SortStrategy.ByDueDateDesc();  return this; }
        public Builder sortByPriorityDesc() { sortStrategy = new SortStrategy.ByPriorityDesc(); return this; }
        public Builder sortByPriorityAsc()  { sortStrategy = new SortStrategy.ByPriorityAsc();  return this; }
        public Builder sortByTitleAsc()     { sortStrategy = new SortStrategy.ByTitleAsc();     return this; }
        public Builder sortByTitleDesc()    { sortStrategy = new SortStrategy.ByTitleDesc();    return this; }

        /** Plug in any custom {@link SortStrategy}. */
        public Builder sortBy(SortStrategy strategy) { sortStrategy = strategy; return this; }

        // ── Pagination ────────────────────────────────────────────────────────

        public Builder page(int page, int pageSize) {
            this.page     = page;
            this.pageSize = pageSize;
            return this;
        }

        public TaskQuery build() { return new TaskQuery(this); }
    }
}
