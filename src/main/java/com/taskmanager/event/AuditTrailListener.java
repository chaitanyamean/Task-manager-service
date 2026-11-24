package com.taskmanager.event;

import com.taskmanager.model.Task;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Observer that keeps an in-memory count of each event type and a full
 * ordered audit trail of every event that has fired.
 *
 * <p>Use {@link #getCounts()} to read the totals, and {@link #getTrail()} to
 * see the full history.</p>
 */
public final class AuditTrailListener implements TaskEventListener {

    /** One entry in the audit trail. */
    public static final class Entry {
        private final TaskEvent event;
        private final String    taskId;
        private final String    taskTitle;

        Entry(TaskEvent event, String taskId, String taskTitle) {
            this.event     = event;
            this.taskId    = taskId;
            this.taskTitle = taskTitle;
        }

        public TaskEvent getEvent()     { return event; }
        public String    getTaskId()    { return taskId; }
        public String    getTaskTitle() { return taskTitle; }

        @Override
        public String toString() {
            return event.name() + " | " + taskId.substring(0, 8) + " | \"" + taskTitle + "\"";
        }
    }

    private final Map<TaskEvent, Integer>  counts = new EnumMap<>(TaskEvent.class);
    private final java.util.List<Entry>    trail  = new java.util.ArrayList<>();

    @Override
    public synchronized void onEvent(TaskEvent event, Task task) {
        counts.merge(event, 1, Integer::sum);
        trail.add(new Entry(event, task.getId(), task.getTitle()));
    }

    /** Returns an unmodifiable snapshot of event counts by type. */
    public synchronized Map<TaskEvent, Integer> getCounts() {
        return Collections.unmodifiableMap(new EnumMap<>(counts));
    }

    /** Returns an unmodifiable snapshot of the full audit trail. */
    public synchronized java.util.List<Entry> getTrail() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(trail));
    }

    /** Returns the count for a specific event type (0 if none have fired). */
    public synchronized int getCount(TaskEvent event) {
        return counts.getOrDefault(event, 0);
    }

    /** Resets all counts and clears the trail — useful between tests. */
    public synchronized void reset() {
        counts.clear();
        trail.clear();
    }

    /** Prints a summary of counts to stdout. */
    public synchronized void printSummary() {
        System.out.println("  AuditTrail summary:");
        for (TaskEvent e : TaskEvent.values()) {
            System.out.printf("    %-10s : %d%n", e.name(), counts.getOrDefault(e, 0));
        }
    }
}
