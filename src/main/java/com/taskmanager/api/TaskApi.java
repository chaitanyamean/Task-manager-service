package com.taskmanager.api;

import com.taskmanager.exception.ApiException;
import com.taskmanager.exception.ConflictException;
import com.taskmanager.model.Task;
import com.taskmanager.model.TaskRequest;
import com.taskmanager.service.IdempotencyStore;
import com.taskmanager.service.IdempotencyStore.LookupResult;
import com.taskmanager.service.TaskQuery;
import com.taskmanager.service.TaskService;

import java.util.List;

/**
 * Public API layer.  Every method returns an {@link ApiResponse}; exceptions
 * never propagate to callers.
 *
 * <p>Steps 4-6 additions:
 * <ul>
 *   <li>{@link #createTaskFromText} – AI-native task creation</li>
 *   <li>{@link #listSubtasks}       – list subtasks of a parent</li>
 *   <li>{@link #breakDownTask}      – LLM/rule-based breakdown</li>
 *   <li>IdempotencyStore wired into createTask, createTaskFromText, breakDownTask</li>
 * </ul>
 * </p>
 */
public class TaskApi {

    private final TaskService      taskService;
    private final IdempotencyStore idempotencyStore;

    public TaskApi(TaskService taskService) {
        this.taskService      = taskService;
        this.idempotencyStore = IdempotencyStore.getInstance();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Create
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * POST /tasks
     * Returns 201 on success, 400 on validation failure, 422 on key conflict.
     */
    public ApiResponse createTask(TaskRequest request) {
        String key = request.getIdempotencyKey();
        String fingerprint = IdempotencyStore.fingerprint(
                request.getTitle(), request.getDescription(),
                request.getCompleted(), request.getDueDate(),
                request.getPriority(), request.getParentTaskId());

        LookupResult lookup = idempotencyStore.lookup(key, fingerprint);
        if (lookup.isHit())      return lookup.getResponse().get();
        if (lookup.isConflict()) return ErrorResponseFactory.from(
                new ConflictException("Idempotency key '" + key +
                        "' was already used with a different request."));

        try {
            Task task = taskService.createTask(request);
            ApiResponse response = ApiResponse.created(taskToJson(task));
            idempotencyStore.store(key, fingerprint, response);
            return response;
        } catch (ApiException ex) {
            return ErrorResponseFactory.from(ex);
        } catch (Exception ex) {
            return ErrorResponseFactory.fromUnknown(ex);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 4 – AI task creation
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * POST /tasks/from-text
     * Converts plain-English {@code text} to a task via the configured TaskParser.
     * Returns 201 on success, 502 if the model call fails or returns invalid data,
     * 503 if the API key is missing, 422 on idempotency key conflict.
     */
    public ApiResponse createTaskFromText(String text, String idempotencyKey) {
        String fingerprint = IdempotencyStore.fingerprint(text, idempotencyKey);

        LookupResult lookup = idempotencyStore.lookup(idempotencyKey, fingerprint);
        if (lookup.isHit())      return lookup.getResponse().get();
        if (lookup.isConflict()) return ErrorResponseFactory.from(
                new ConflictException("Idempotency key '" + idempotencyKey +
                        "' was already used with a different request."));

        try {
            Task task = taskService.createTaskFromText(text, idempotencyKey);
            ApiResponse response = ApiResponse.created(taskToJson(task));
            idempotencyStore.store(idempotencyKey, fingerprint, response);
            return response;
        } catch (ApiException ex) {
            return ErrorResponseFactory.from(ex);
        } catch (Exception ex) {
            return ErrorResponseFactory.fromUnknown(ex);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Read
    // ══════════════════════════════════════════════════════════════════════════

    /** GET /tasks/{id} — returns 200 or 404. */
    public ApiResponse getTask(String id) {
        try {
            Task task = taskService.getTaskById(id);
            return ApiResponse.ok(taskToJson(task));
        } catch (ApiException ex) {
            return ErrorResponseFactory.from(ex);
        } catch (Exception ex) {
            return ErrorResponseFactory.fromUnknown(ex);
        }
    }

    /**
     * GET /tasks — with optional filtering, sorting, and pagination.
     */
    public ApiResponse getAllTasks(String completedFilter, String priorityFilter,
                                   String sortBy, int page, int pageSize) {
        try {
            List<Task> tasks = taskService.getAllTasks(
                    completedFilter, priorityFilter, sortBy, page, pageSize);
            return ApiResponse.ok(taskListToJson(tasks));
        } catch (ApiException ex) {
            return ErrorResponseFactory.from(ex);
        } catch (Exception ex) {
            return ErrorResponseFactory.fromUnknown(ex);
        }
    }

    /** Convenience overload — no filters. */
    public ApiResponse getAllTasks() {
        return getAllTasks(null, null, null, 0, 0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 7 – queryTasks
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * GET /tasks/query
     * Accepts a pre-built {@link TaskQuery} and returns the matching tasks.
     * Filters, keyword search, sorting, and pagination all compose in one call.
     */
    public ApiResponse queryTasks(TaskQuery query) {
        try {
            List<Task> tasks = taskService.queryTasks(query);
            return ApiResponse.ok(taskListToJson(tasks));
        } catch (ApiException ex) {
            return ErrorResponseFactory.from(ex);
        } catch (Exception ex) {
            return ErrorResponseFactory.fromUnknown(ex);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 5 – Subtasks
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * GET /tasks/{parentId}/subtasks
     * Returns 200 with a JSON array of subtasks, or 404 if parent not found.
     */
    public ApiResponse listSubtasks(String parentId) {
        try {
            List<Task> subtasks = taskService.listSubtasks(parentId);
            return ApiResponse.ok(taskListToJson(subtasks));
        } catch (ApiException ex) {
            return ErrorResponseFactory.from(ex);
        } catch (Exception ex) {
            return ErrorResponseFactory.fromUnknown(ex);
        }
    }

    /**
     * POST /tasks/{id}/breakdown
     * Breaks a task into subtasks. Returns 201 on success, 400 if the task is
     * itself a subtask, 502 if the strategy call fails or produces invalid data.
     */
    public ApiResponse breakDownTask(String id, String idempotencyKey) {
        String fingerprint = IdempotencyStore.fingerprint(id, idempotencyKey);

        LookupResult lookup = idempotencyStore.lookup(idempotencyKey, fingerprint);
        if (lookup.isHit())      return lookup.getResponse().get();
        if (lookup.isConflict()) return ErrorResponseFactory.from(
                new ConflictException("Idempotency key '" + idempotencyKey +
                        "' was already used with a different request."));

        try {
            List<Task> subtasks = taskService.breakDownTask(id, idempotencyKey);
            ApiResponse response = ApiResponse.created(taskListToJson(subtasks));
            idempotencyStore.store(idempotencyKey, fingerprint, response);
            return response;
        } catch (ApiException ex) {
            return ErrorResponseFactory.from(ex);
        } catch (Exception ex) {
            return ErrorResponseFactory.fromUnknown(ex);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Update / Delete
    // ══════════════════════════════════════════════════════════════════════════

    /** PUT /tasks/{id} — returns 200, 400, or 404. */
    public ApiResponse updateTask(String id, TaskRequest request) {
        try {
            Task task = taskService.updateTask(id, request);
            return ApiResponse.ok(taskToJson(task));
        } catch (ApiException ex) {
            return ErrorResponseFactory.from(ex);
        } catch (Exception ex) {
            return ErrorResponseFactory.fromUnknown(ex);
        }
    }

    /** DELETE /tasks/{id} — cascades to subtasks; returns 200 or 404. */
    public ApiResponse deleteTask(String id) {
        try {
            taskService.deleteTask(id);
            return ApiResponse.ok("{ \"message\": \"Task and its subtasks deleted successfully\" }");
        } catch (ApiException ex) {
            return ErrorResponseFactory.from(ex);
        } catch (Exception ex) {
            return ErrorResponseFactory.fromUnknown(ex);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JSON serialisation helpers
    // ══════════════════════════════════════════════════════════════════════════

    static String taskToJson(Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": \"").append(esc(task.getId())).append("\",\n");
        sb.append("  \"title\": \"").append(esc(task.getTitle())).append("\",\n");
        sb.append("  \"description\": \"").append(esc(task.getDescription())).append("\",\n");
        sb.append("  \"completed\": ").append(task.isCompleted()).append(",\n");
        sb.append("  \"dueDate\": ")
          .append(task.getDueDate() != null ? "\"" + task.getDueDate() + "\"" : "null")
          .append(",\n");
        sb.append("  \"priority\": \"").append(task.getPriority()).append("\",\n");
        sb.append("  \"parentTaskId\": ")
          .append(task.getParentTaskId() != null
                  ? "\"" + esc(task.getParentTaskId()) + "\"" : "null")
          .append(",\n");
        sb.append("  \"subtaskIds\": ").append(stringListToJson(task.getSubtaskIds())).append(",\n");
        sb.append("  \"idempotencyKey\": ")
          .append(task.getIdempotencyKey() != null
                  ? "\"" + esc(task.getIdempotencyKey()) + "\"" : "null")
          .append("\n");
        sb.append("}");
        return sb.toString();
    }

    private static String taskListToJson(List<Task> tasks) {
        if (tasks.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < tasks.size(); i++) {
            String indented = taskToJson(tasks.get(i))
                    .lines()
                    .map(l -> "  " + l)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            sb.append(indented);
            if (i < tasks.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String stringListToJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(esc(list.get(i))).append("\"");
            if (i < list.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
