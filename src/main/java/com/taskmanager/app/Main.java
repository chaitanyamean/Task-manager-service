package com.taskmanager.app;

import com.taskmanager.api.ApiResponse;
import com.taskmanager.api.TaskApi;
import com.taskmanager.event.AuditTrailListener;
import com.taskmanager.event.ConsoleLogListener;
import com.taskmanager.event.TaskEvent;
import com.taskmanager.model.Priority;
import com.taskmanager.model.TaskRequest;
import com.taskmanager.parser.ParserFactory;
import com.taskmanager.repository.InMemoryTaskRepository;
import com.taskmanager.service.BreakdownStrategy;
import com.taskmanager.service.IdempotencyStore;
import com.taskmanager.service.RuleBasedBreakdownStrategy;
import com.taskmanager.service.TaskQuery;
import com.taskmanager.service.TaskService;

/**
 * Entry point. Demonstrates every API operation (Steps 1–8) and prints results.
 * No web framework — all calls are plain Java method invocations.
 *
 * Run:               java -cp out com.taskmanager.app.Main
 * With AI features:  LLM_API_KEY=sk-ant-... java -cp out com.taskmanager.app.Main
 */
public class Main {

    public static void main(String[] args) {

        banner("Task Manager started");

        // ── Wiring (manual dependency injection) ─────────────────────────────
        InMemoryTaskRepository repository = InMemoryTaskRepository.getInstance();
        IdempotencyStore       idem       = IdempotencyStore.getInstance();
        BreakdownStrategy      breakdown  = new RuleBasedBreakdownStrategy();
        TaskService            service    = new TaskService(
                repository, ParserFactory.create(), breakdown, idem);
        TaskApi                api        = new TaskApi(service);

        // ── Step 8: register event listeners before any operations ───────────
        ConsoleLogListener  logger = new ConsoleLogListener();
        AuditTrailListener  audit  = new AuditTrailListener();
        service.addListener(logger);
        service.addListener(audit);

        repository.clear();
        idem.clear();


        // ══════════════════════════════════════════════════════════════════════
        // STEPS 1-3: Basic CRUD + validation
        // ══════════════════════════════════════════════════════════════════════

        section("1. Create task (valid) — fires CREATED event");
        ApiResponse createResp = api.createTask(new TaskRequest.Builder()
                .title("Buy groceries")
                .description("Milk, eggs, bread, and cheese")
                .completed("false")
                .dueDate("2026-12-01")
                .priority("HIGH")
                .build());
        print(createResp);
        String groceryId = extractId(createResp.getBody());

        section("2. Create task with idempotency key");
        TaskRequest rentReq = new TaskRequest.Builder()
                .title("Pay rent")
                .description("Bank transfer on the 1st of every month")
                .completed("false").priority("HIGH")
                .idempotencyKey("pay-rent-2026-12")
                .build();
        print(api.createTask(rentReq));

        section("2b. Same key + same request → cached, no duplicate, no new event");
        int countBefore = audit.getCount(TaskEvent.CREATED);
        print(api.createTask(rentReq));
        System.out.println("  CREATED count unchanged: " +
                (audit.getCount(TaskEvent.CREATED) == countBefore));

        section("2c. Same key + different request → 422 CONFLICT");
        print(api.createTask(new TaskRequest.Builder()
                .title("Pay rent CHANGED").description("Different").completed("false")
                .priority("LOW").idempotencyKey("pay-rent-2026-12").build()));

        section("3. Get task by ID");
        print(api.getTask(groceryId));

        section("4. Get all tasks");
        print(api.getAllTasks());

        section("5. Update task (mark completed) — fires COMPLETED event");
        print(api.updateTask(groceryId, new TaskRequest.Builder()
                .completed("true").priority("MEDIUM").build()));

        section("6. Validation errors — all 5 fields at once");
        print(api.createTask(new TaskRequest.Builder()
                .title("").description("").completed("maybe")
                .dueDate("2026-02-30").priority("URGENT").build()));

        section("7. 404 for unknown ID");
        print(api.getTask("non-existent-id-999"));


        // ══════════════════════════════════════════════════════════════════════
        // STEP 4: AI-native task creation (rule-based parser, no key needed)
        // ══════════════════════════════════════════════════════════════════════

        section("8. createTaskFromText — 'Pay electricity bill tomorrow'");
        print(api.createTaskFromText("Pay electricity bill tomorrow", null));

        section("9. createTaskFromText — 'Remind me to call the dentist next Monday'");
        print(api.createTaskFromText("Remind me to call the dentist next Monday", null));

        section("10. createTaskFromText — idempotency cache hit");
        api.createTaskFromText("Submit expenses every month on the 15th", "submit-expenses");
        ApiResponse cached = api.createTaskFromText(
                "Submit expenses every month on the 15th", "submit-expenses");
        System.out.println("  Cached (same id): " + cached.getStatusCode() + " " +
                extractId(cached.getBody()));

        section("11. createTaskFromText — no API key → 503");
        if (System.getenv("LLM_API_KEY") == null) {
            TaskService llmSvc = new TaskService(repository,
                    ParserFactory.llm(new com.taskmanager.parser.LlmClient()),
                    breakdown, idem);
            print(new TaskApi(llmSvc).createTaskFromText("Book a flight", null));
        } else {
            System.out.println("  (LLM_API_KEY is set — skipping 503 demo)");
        }


        // ══════════════════════════════════════════════════════════════════════
        // STEP 5: Subtasks & breakdown
        // ══════════════════════════════════════════════════════════════════════

        section("12. Create parent task for breakdown");
        ApiResponse parentResp = api.createTask(new TaskRequest.Builder()
                .title("Plan team offsite")
                .description("Book venue and arrange catering and prepare agenda")
                .completed("false").priority("HIGH").build());
        print(parentResp);
        String parentId = extractId(parentResp.getBody());

        section("13. breakDownTask — rule-based splits on 'and'");
        ApiResponse bdResp = api.breakDownTask(parentId, "breakdown-offsite");
        print(bdResp);

        section("13b. Retry breakDownTask same key → cached");
        print(api.breakDownTask(parentId, "breakdown-offsite"));

        section("14. listSubtasks");
        print(api.listSubtasks(parentId));

        section("15. breakDownTask on a subtask → 400");
        String firstSubId = extractId(api.listSubtasks(parentId).getBody()
                .replace("[\n  ", "").replace("  {", "{"));
        if (!firstSubId.equals("unknown")) {
            print(api.breakDownTask(firstSubId, null));
        }

        section("16. Cascade delete — repo printed before & after");
        print(api.deleteTask(parentId));


        // ══════════════════════════════════════════════════════════════════════
        // STEP 7: TaskQuery — filters, keyword search, sort, pagination
        // ══════════════════════════════════════════════════════════════════════

        // Seed fresh tasks for query demo
        repository.clear(); idem.clear(); audit.reset();
        api.createTask(new TaskRequest.Builder().title("Alpha project")
                .description("first task alpha").completed("false")
                .priority("LOW").dueDate("2027-03-01").build());
        api.createTask(new TaskRequest.Builder().title("Beta report")
                .description("second task").completed("true")
                .priority("HIGH").dueDate("2027-01-15").build());
        api.createTask(new TaskRequest.Builder().title("Gamma review")
                .description("third task alpha content").completed("false")
                .priority("MEDIUM").dueDate("2027-02-10").build());

        section("17. queryTasks — filterByCompleted(false)");
        print(api.queryTasks(new TaskQuery.Builder()
                .filterByCompleted(false).build()));

        section("18. queryTasks — filterByPriority(HIGH)");
        print(api.queryTasks(new TaskQuery.Builder()
                .filterByPriority(Priority.HIGH).build()));

        section("19. queryTasks — keyword search 'alpha'");
        print(api.queryTasks(new TaskQuery.Builder()
                .filterByKeyword("alpha").build()));

        section("20. queryTasks — sort by dueDate ascending");
        print(api.queryTasks(new TaskQuery.Builder()
                .sortByDueDateAsc().build()));

        section("21. queryTasks — sort by title ascending");
        print(api.queryTasks(new TaskQuery.Builder()
                .sortByTitleAsc().build()));

        section("22. queryTasks — filter + sort + pagination combined");
        print(api.queryTasks(new TaskQuery.Builder()
                .filterByCompleted(false)
                .sortByDueDateAsc()
                .page(1, 1)
                .build()));

        section("23. queryTasks — page beyond last → []");
        print(api.queryTasks(new TaskQuery.Builder()
                .page(99, 10).build()));

        section("24. queryTasks — topLevelOnly (no subtasks)");
        print(api.queryTasks(new TaskQuery.Builder()
                .topLevelOnly().build()));


        // ══════════════════════════════════════════════════════════════════════
        // STEP 8: Observer — audit summary + removeListener proof
        // ══════════════════════════════════════════════════════════════════════

        section("25. Audit trail summary after steps 17-24");
        audit.printSummary();
        System.out.println("  Full trail:");
        audit.getTrail().forEach(e -> System.out.println("    " + e));

        section("26. removeListener — creating task after remove does NOT increment audit");
        service.removeListener(audit);
        int beforeRemove = audit.getCount(TaskEvent.CREATED);
        api.createTask(new TaskRequest.Builder()
                .title("After remove").description("Should not appear in audit")
                .completed("false").build());
        System.out.println("  Audit CREATED count before remove: " + beforeRemove);
        System.out.println("  Audit CREATED count after  remove: " + audit.getCount(TaskEvent.CREATED));
        System.out.println("  Counts equal (listener stopped): " +
                (audit.getCount(TaskEvent.CREATED) == beforeRemove));

        // ConsoleLogListener is still active — its log line prints above ^^


        banner("All operations complete.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void banner(String title) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  " + title);
        System.out.println("=".repeat(60));
    }

    private static void section(String title) {
        System.out.println("\n── " + title);
    }

    private static void print(ApiResponse r) {
        System.out.println(r.toJson());
    }

    private static String extractId(String json) {
        int idx = json.indexOf("\"id\": \"");
        if (idx < 0) return "unknown";
        int start = idx + 7;
        int end   = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : "unknown";
    }
}
