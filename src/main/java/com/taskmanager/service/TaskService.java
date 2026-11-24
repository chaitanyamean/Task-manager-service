package com.taskmanager.service;

import com.taskmanager.event.TaskEvent;
import com.taskmanager.event.TaskEventListener;
import com.taskmanager.exception.BadGatewayException;
import com.taskmanager.exception.NotFoundException;
import com.taskmanager.exception.ValidationException;
import com.taskmanager.exception.ValidationException.FieldError;
import com.taskmanager.model.Priority;
import com.taskmanager.model.Task;
import com.taskmanager.model.TaskRequest;
import com.taskmanager.parser.ParsedTask;
import com.taskmanager.parser.TaskParser;
import com.taskmanager.repository.TaskRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Business logic layer for task management.
 *
 * <p>Depends on interfaces only (Dependency Inversion):</p>
 * <ul>
 *   <li>{@link TaskRepository}      – storage</li>
 *   <li>{@link TaskParser}          – plain-English → ParsedTask (Strategy)</li>
 *   <li>{@link BreakdownStrategy}   – Task → subtask list (Strategy)</li>
 *   <li>{@link IdempotencyStore}    – safe retries (Singleton)</li>
 *   <li>{@link TaskEventListener}   – Observer notifications</li>
 * </ul>
 */
public class TaskService {

    private final TaskRepository    repository;
    private final TaskParser        parser;
    private final BreakdownStrategy breakdown;
    private final IdempotencyStore  idempotencyStore;

    // ── Step 8: Observer – thread-safe listener list ──────────────────────────
    private final List<TaskEventListener> listeners = new CopyOnWriteArrayList<>();

    /** Full constructor — all dependencies injected. */
    public TaskService(TaskRepository    repository,
                       TaskParser        parser,
                       BreakdownStrategy breakdown,
                       IdempotencyStore  idempotencyStore) {
        this.repository       = repository;
        this.parser           = parser;
        this.breakdown        = breakdown;
        this.idempotencyStore = idempotencyStore;
    }

    /** Minimal constructor — CRUD only. Parser/breakdown must be supplied later if used. */
    public TaskService(TaskRepository repository) {
        this(repository, null, null, IdempotencyStore.getInstance());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 8 – Observer: listener management
    // ══════════════════════════════════════════════════════════════════════════

    /** Registers a listener. No-op if already registered. */
    public void addListener(TaskEventListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    /** Unregisters a listener. After this call it receives no further events. */
    public void removeListener(TaskEventListener listener) {
        listeners.remove(listener);
    }

    /** Fires an event to every registered listener. Listener errors are swallowed. */
    private void fire(TaskEvent event, Task task) {
        for (TaskEventListener l : listeners) {
            try { l.onEvent(event, task); }
            catch (Exception ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 2 / Step 6 – createTask
    // ══════════════════════════════════════════════════════════════════════════

    public Task createTask(TaskRequest request) {
        String key = request.getIdempotencyKey();

        // Repository-level idempotency (legacy path)
        if (key != null && !key.isBlank()) {
            Optional<Task> existing = repository.findByIdempotencyKey(key);
            if (existing.isPresent()) return existing.get();
        }

        validate(request, true);

        Task task = new Task.Builder()
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .completed(Boolean.parseBoolean(request.getCompleted()))
                .dueDate(parseDueDate(request.getDueDate()))
                .priority(parsePriority(request.getPriority()))
                .parentTaskId(request.getParentTaskId())
                .idempotencyKey(key)
                .build();

        // Link to parent
        if (request.getParentTaskId() != null && !request.getParentTaskId().isBlank()) {
            Task parent = repository.findById(request.getParentTaskId())
                    .orElseThrow(() -> new NotFoundException("Parent task", request.getParentTaskId()));
            repository.save(parent.toBuilder().addSubtaskId(task.getId()).build());
        }

        Task saved = repository.save(task);
        fire(TaskEvent.CREATED, saved);
        return saved;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 4 – createTaskFromText
    // ══════════════════════════════════════════════════════════════════════════

    public Task createTaskFromText(String text, String idempotencyKey) {
        if (text == null || text.isBlank())
            throw new ValidationException("text", "must not be empty");

        String today  = LocalDate.now().toString();
        ParsedTask parsed = parser.parse(text, today);

        TaskRequest request = new TaskRequest.Builder()
                .title(parsed.getTitle())
                .description(parsed.getDescription())
                .completed("false")
                .dueDate(parsed.getDueDate())
                .priority("MEDIUM")
                .idempotencyKey(idempotencyKey)
                .build();

        // "Never trust the model"
        try {
            validate(request, true);
        } catch (ValidationException ve) {
            String details = ve.getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getIssue())
                    .collect(Collectors.joining("; "));
            throw new BadGatewayException("LLM returned invalid task data — " + details);
        }

        return createTask(request);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 7 – queryTasks (TaskQuery: filters + sort + pagination)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Executes a {@link TaskQuery} against all stored tasks.
     * Filters, keyword search, sorting, and pagination all compose in one call.
     */
    public List<Task> queryTasks(TaskQuery query) {
        return query.execute(new ArrayList<>(repository.findAll()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Read / Update / Delete
    // ══════════════════════════════════════════════════════════════════════════

    public Task getTaskById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task", id));
    }

    public List<Task> getAllTasks(String completedFilter, String priorityFilter,
                                  String sortBy, int page, int pageSize) {
        TaskQuery.Builder qb = new TaskQuery.Builder();
        if (completedFilter != null && !completedFilter.isBlank())
            qb.filterByCompleted(Boolean.parseBoolean(completedFilter));
        if (priorityFilter != null && !priorityFilter.isBlank())
            qb.filterByPriority(parsePriority(priorityFilter));
        if ("dueDate".equalsIgnoreCase(sortBy))    qb.sortByDueDateAsc();
        if ("priority".equalsIgnoreCase(sortBy))   qb.sortByPriorityDesc();
        if (page > 0 && pageSize > 0)              qb.page(page, pageSize);
        return queryTasks(qb.build());
    }

    public Task updateTask(String id, TaskRequest request) {
        Task existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task", id));
        validate(request, false);

        Task.Builder builder = existing.toBuilder();
        if (request.getTitle() != null)       builder.title(request.getTitle().trim());
        if (request.getDescription() != null) builder.description(request.getDescription().trim());
        if (request.getCompleted() != null)   builder.completed(Boolean.parseBoolean(request.getCompleted()));
        if (request.getDueDate() != null)     builder.dueDate(parseDueDate(request.getDueDate()));
        if (request.getPriority() != null)    builder.priority(parsePriority(request.getPriority()));

        Task updated = repository.save(builder.build());
        TaskEvent event = updated.isCompleted() && !existing.isCompleted()
                ? TaskEvent.COMPLETED : TaskEvent.UPDATED;
        fire(event, updated);
        return updated;
    }

    /** Deletes a task and cascades to its subtasks. Prints repo before/after. */
    public void deleteTask(String id) {
        Task task = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task", id));

        System.out.println("\n[Repository BEFORE delete of '" + task.getTitle() + "']");
        printRepository();

        for (String subtaskId : task.getSubtaskIds()) repository.delete(subtaskId);
        repository.delete(id);

        System.out.println("[Repository AFTER delete]");
        printRepository();

        fire(TaskEvent.DELETED, task);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Step 5 – Subtasks & breakdown
    // ══════════════════════════════════════════════════════════════════════════

    public List<Task> listSubtasks(String parentId) {
        Task parent = repository.findById(parentId)
                .orElseThrow(() -> new NotFoundException("Task", parentId));
        List<Task> subtasks = new ArrayList<>();
        for (String sid : parent.getSubtaskIds())
            repository.findById(sid).ifPresent(subtasks::add);
        return subtasks;
    }

    public List<Task> breakDownTask(String id, String idempotencyKey) {
        Task parent = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task", id));

        if (parent.getParentTaskId() != null && !parent.getParentTaskId().isBlank())
            throw new ValidationException("id",
                    "A subtask cannot be broken down further or given its own subtasks");

        List<TaskRequest> proposals = breakdown.breakdown(
                parent.getTitle(), parent.getDescription());

        // Validate all before saving any
        List<FieldError> allErrors = new ArrayList<>();
        for (int i = 0; i < proposals.size(); i++) {
            try { validate(proposals.get(i), true); }
            catch (ValidationException ve) {
                for (FieldError fe : ve.getFieldErrors())
                    allErrors.add(new FieldError("subtask[" + i + "]." + fe.getField(), fe.getIssue()));
            }
        }
        if (!allErrors.isEmpty())
            throw new BadGatewayException("Breakdown strategy returned invalid subtask data: " +
                    allErrors.stream().map(e -> e.getField() + ": " + e.getIssue())
                             .collect(Collectors.joining("; ")));

        List<Task> created = new ArrayList<>();
        for (TaskRequest req : proposals) {
            TaskRequest withParent = new TaskRequest.Builder()
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .completed("false")
                    .dueDate(req.getDueDate())
                    .priority(req.getPriority() != null ? req.getPriority() : "MEDIUM")
                    .parentTaskId(id)
                    .idempotencyKey(idempotencyKey != null
                            ? idempotencyKey + "-sub-" + (created.size() + 1) : null)
                    .build();
            created.add(createTask(withParent));
        }
        return created;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Validation
    // ══════════════════════════════════════════════════════════════════════════

    void validate(TaskRequest request, boolean requireAll) {
        List<FieldError> errors = new ArrayList<>();

        if (requireAll || request.getTitle() != null) {
            if (request.getTitle() == null || request.getTitle().isBlank())
                errors.add(new FieldError("title", "must not be empty"));
        }
        if (requireAll || request.getDescription() != null) {
            if (request.getDescription() == null || request.getDescription().isBlank())
                errors.add(new FieldError("description", "must not be empty"));
        }
        if (requireAll || request.getCompleted() != null) {
            String c = request.getCompleted();
            if (c == null || (!c.equals("true") && !c.equals("false")))
                errors.add(new FieldError("completed", "must be exactly \"true\" or \"false\""));
        }
        if (request.getDueDate() != null && !request.getDueDate().isBlank()) {
            if (!isValidDate(request.getDueDate()))
                errors.add(new FieldError("dueDate", "must be a valid date in YYYY-MM-DD format"));
        }
        if (request.getPriority() != null && !request.getPriority().isBlank()) {
            try { Priority.valueOf(request.getPriority().toUpperCase()); }
            catch (IllegalArgumentException e) {
                errors.add(new FieldError("priority", "must be LOW, MEDIUM, or HIGH"));
            }
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Date helpers
    // ══════════════════════════════════════════════════════════════════════════

    static boolean isValidDate(String s) {
        if (s == null) return false;
        s = s.trim();
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        int year  = Integer.parseInt(s.substring(0, 4));
        int month = Integer.parseInt(s.substring(5, 7));
        int day   = Integer.parseInt(s.substring(8, 10));
        if (month < 1 || month > 12) return false;
        return day >= 1 && day <= daysInMonth(year, month);
    }

    private static int daysInMonth(int year, int month) {
        return switch (month) {
            case 1,3,5,7,8,10,12 -> 31;
            case 4,6,9,11        -> 30;
            case 2 -> isLeapYear(year) ? 29 : 28;
            default -> 0;
        };
    }

    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    private static LocalDate parseDueDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s.trim());
    }

    private static Priority parsePriority(String s) {
        if (s == null || s.isBlank()) return Priority.MEDIUM;
        return Priority.valueOf(s.trim().toUpperCase());
    }

    // ── Debug helper ──────────────────────────────────────────────────────────

    private void printRepository() {
        List<Task> all = repository.findAll();
        if (all.isEmpty()) { System.out.println("  (empty)"); return; }
        for (Task t : all) {
            System.out.printf("  [%s] %s (subtasks: %d, parent: %s)%n",
                    t.getId().substring(0, 8), t.getTitle(),
                    t.getSubtaskIds().size(),
                    t.getParentTaskId() == null ? "none" : t.getParentTaskId().substring(0, 8));
        }
    }
}
