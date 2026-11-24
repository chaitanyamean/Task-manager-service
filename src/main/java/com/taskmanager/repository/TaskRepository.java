package com.taskmanager.repository;

import com.taskmanager.model.Task;

import java.util.List;
import java.util.Optional;

/**
 * Contract for task persistence.
 * Business logic depends on this interface, not on any concrete class (Dependency Inversion).
 */
public interface TaskRepository {

    /** Persists a new task or overwrites an existing one with the same ID. */
    Task save(Task task);

    /** Returns the task with the given ID, or empty if it does not exist. */
    Optional<Task> findById(String id);

    /** Returns all stored tasks. */
    List<Task> findAll();

    /** Removes the task with the given ID. Returns true if it existed. */
    boolean delete(String id);

    /** Returns true if a task with the given idempotency key already exists. */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /** Returns the task stored under the given idempotency key, or empty. */
    Optional<Task> findByIdempotencyKey(String idempotencyKey);
}
