package com.taskmanager.service;

import com.taskmanager.model.TaskRequest;

import java.util.List;

/**
 * Strategy interface for breaking a task into subtask requests.
 *
 * <p>TaskService depends only on this interface (Dependency Inversion).
 * Implementations may call an LLM, apply simple rules, or do anything else —
 * the service never knows or cares which one is active.</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Returns an ordered list of {@link TaskRequest} objects — one per proposed subtask.</li>
 *   <li>Every returned request has at least {@code title} and {@code description} set.</li>
 *   <li>Throws {@link com.taskmanager.exception.BadGatewayException} if an upstream call fails.</li>
 *   <li>Throws {@link com.taskmanager.exception.ServiceUnavailableException} if the API key is absent.</li>
 * </ul>
 */
public interface BreakdownStrategy {

    /**
     * Produces a list of subtask requests for the given parent task title/description.
     *
     * @param title       parent task title
     * @param description parent task description
     * @return non-null, possibly empty list of subtask requests
     */
    List<TaskRequest> breakdown(String title, String description);
}
