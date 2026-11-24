package com.taskmanager.repository;

import com.taskmanager.model.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of TaskRepository.
 *
 * Implemented as a Singleton: only one instance exists for the lifetime
 * of the application, ensuring a single shared store.
 */
public final class InMemoryTaskRepository implements TaskRepository {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static InMemoryTaskRepository instance;

    /** Returns the single shared instance, creating it on first call. */
    public static synchronized InMemoryTaskRepository getInstance() {
        if (instance == null) {
            instance = new InMemoryTaskRepository();
        }
        return instance;
    }

    /** Private constructor prevents external instantiation (use getInstance() normally). */
    private InMemoryTaskRepository() {}

    /**
     * Creates a fresh, independent instance for testing.
     * Tests should use this instead of the Singleton to avoid shared state.
     */
    public static InMemoryTaskRepository createFresh() {
        return new InMemoryTaskRepository();
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    // Primary store: task ID → Task
    private final Map<String, Task> store = new HashMap<>();

    // Secondary index: idempotency key → task ID (for O(1) lookup)
    private final Map<String, String> idempotencyIndex = new HashMap<>();

    // ── TaskRepository implementation ─────────────────────────────────────────

    @Override
    public Task save(Task task) {
        store.put(task.getId(), task);
        if (task.getIdempotencyKey() != null && !task.getIdempotencyKey().isBlank()) {
            idempotencyIndex.put(task.getIdempotencyKey(), task.getId());
        }
        return task;
    }

    @Override
    public Optional<Task> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Task> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean delete(String id) {
        Task removed = store.remove(id);
        if (removed != null && removed.getIdempotencyKey() != null) {
            idempotencyIndex.remove(removed.getIdempotencyKey());
        }
        return removed != null;
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return idempotencyIndex.containsKey(idempotencyKey);
    }

    @Override
    public Optional<Task> findByIdempotencyKey(String idempotencyKey) {
        String taskId = idempotencyIndex.get(idempotencyKey);
        if (taskId == null) return Optional.empty();
        return findById(taskId);
    }

    /**
     * Clears all data — useful for testing without creating a new instance.
     */
    public void clear() {
        store.clear();
        idempotencyIndex.clear();
    }
}
