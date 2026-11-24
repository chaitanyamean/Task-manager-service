package com.taskmanager.event;

import com.taskmanager.model.Task;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Observer that prints a one-line log entry to stdout for every task event.
 *
 * <p>Example output:</p>
 * <pre>
 * [2026-11-24 10:15:03] CREATED  | id=7588f7b6 | "Buy groceries" | priority=HIGH
 * [2026-11-24 10:15:04] UPDATED  | id=7588f7b6 | "Buy groceries" | priority=MEDIUM
 * [2026-11-24 10:15:05] COMPLETED| id=7588f7b6 | "Buy groceries"
 * [2026-11-24 10:15:06] DELETED  | id=7588f7b6 | "Buy groceries"
 * </pre>
 */
public final class ConsoleLogListener implements TaskEventListener {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void onEvent(TaskEvent event, Task task) {
        String timestamp = LocalDateTime.now().format(FMT);
        String idShort   = task.getId().substring(0, 8);
        System.out.printf("[%s] %-9s | id=%s | \"%s\" | priority=%s%n",
                timestamp, event.name(), idShort, task.getTitle(), task.getPriority());
    }
}
