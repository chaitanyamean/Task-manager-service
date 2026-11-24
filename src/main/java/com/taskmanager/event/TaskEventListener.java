package com.taskmanager.event;

import com.taskmanager.model.Task;

/**
 * Observer interface for task lifecycle events.
 *
 * <p>Any class that wants to react to task changes implements this interface
 * and registers itself with {@code TaskService.addListener()}. The service
 * fires {@link #onEvent} after every create, update, complete, or delete
 * without knowing anything about the listener's behaviour.</p>
 *
 * <h3>To add a new listener</h3>
 * <ol>
 *   <li>Implement this interface.</li>
 *   <li>Call {@code taskService.addListener(myListener)}.</li>
 *   <li>No changes to TaskService or any other class are required.</li>
 * </ol>
 */
public interface TaskEventListener {

    /**
     * Called by TaskService after a task lifecycle event occurs.
     *
     * @param event the type of event that occurred
     * @param task  the task involved in the event (never null)
     */
    void onEvent(TaskEvent event, Task task);
}
