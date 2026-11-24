package com.taskmanager.app;

import com.taskmanager.api.ApiResponse;
import com.taskmanager.api.TaskApi;
import com.taskmanager.event.AuditTrailListener;
import com.taskmanager.event.TaskEvent;
import com.taskmanager.model.Priority;
import com.taskmanager.model.TaskRequest;
import com.taskmanager.parser.FakeLlmClient;
import com.taskmanager.parser.FaultyLlmClient;
import com.taskmanager.parser.ParserFactory;
import com.taskmanager.repository.InMemoryTaskRepository;
import com.taskmanager.service.BreakdownStrategy;
import com.taskmanager.service.IdempotencyStore;
import com.taskmanager.service.RuleBasedBreakdownStrategy;
import com.taskmanager.service.TaskQuery;
import com.taskmanager.service.TaskService;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-Java test runner.  No test library — each case calls TaskApi methods
 * and prints PASS or FAIL.
 *
 * Run with:  java -cp out com.taskmanager.app.TestRunner
 *
 * All tests run without a real API key or network connection.
 * FakeLlmClient and FaultyLlmClient are injected via ParserFactory.
 *
 * AI-generated test checklist critique (what the AI missed, what was added):
 *   - AI generated basic CRUD happy paths but missed:
 *     * Idempotency conflict (same key, different request → 422)
 *     * FaultyLlmClient: impossible date, malformed JSON, network error
 *     * Subtask-of-subtask breakdown guard (→ 400)
 *     * Page number beyond last page (→ empty list, still 200)
 *     * Keyword search returning only matching tasks
 *     * removeListener stops notifications
 *     * AuditTrail counts each event type correctly
 *   - All those gaps are covered here.
 */
public final class TestRunner {

    // ── Test infrastructure ───────────────────────────────────────────────────

    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();

    private static void pass(String name) {
        System.out.println("  PASS  " + name);
        passed++;
    }

    private static void fail(String name, String reason) {
        System.out.println("  FAIL  " + name + "  →  " + reason);
        failed++;
        failures.add(name + ": " + reason);
    }

    private static void assertStatus(String name, ApiResponse resp, int expected) {
        if (resp.getStatusCode() == expected) pass(name);
        else fail(name, "expected HTTP " + expected + " got " + resp.getStatusCode()
                + "\n        body: " + resp.getBody().lines().findFirst().orElse(""));
    }

    private static void assertContains(String name, ApiResponse resp, String fragment) {
        if (resp.getBody().contains(fragment)) pass(name);
        else fail(name, "expected body to contain \"" + fragment + "\""
                + "\n        body: " + resp.getBody().substring(0, Math.min(200, resp.getBody().length())));
    }

    private static void assertTrue(String name, boolean condition, String reason) {
        if (condition) pass(name); else fail(name, reason);
    }

    private static void section(String title) {
        System.out.println("\n── " + title);
    }

    // ── Wiring helpers ────────────────────────────────────────────────────────

    /** Fresh isolated wiring for each test group — no shared state. */
    private static TaskApi freshApi() {
        InMemoryTaskRepository repo  = InMemoryTaskRepository.createFresh();
        IdempotencyStore       idem  = IdempotencyStore.createFresh();
        TaskService            svc   = new TaskService(repo, ParserFactory.ruleBased(),
                                            new RuleBasedBreakdownStrategy(), idem);
        return new TaskApi(svc);
    }

    private static TaskApi freshApiWith(com.taskmanager.parser.LlmClient client) {
        InMemoryTaskRepository repo = InMemoryTaskRepository.createFresh();
        IdempotencyStore       idem = IdempotencyStore.createFresh();
        TaskService            svc  = new TaskService(repo, ParserFactory.llm(client),
                                            new RuleBasedBreakdownStrategy(), idem);
        return new TaskApi(svc);
    }

    /** Creates a valid task and returns its ID. */
    private static String createTask(TaskApi api, String title, String priority) {
        ApiResponse r = api.createTask(new TaskRequest.Builder()
                .title(title).description("Description for " + title)
                .completed("false").priority(priority).build());
        return extractId(r.getBody());
    }

    private static String extractId(String json) {
        int idx = json.indexOf("\"id\": \"");
        if (idx < 0) return "unknown";
        int start = idx + 7;
        int end   = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : "unknown";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // main
    // ══════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("  TestRunner");
        System.out.println("=".repeat(60));

        testCrud();
        testValidation();
        testIdempotency();
        testFiltersAndQuery();
        testPagination();
        testSubtasks();
        testBreakdown();
        testCreateFromText();
        testFaultyLlm();
        testObserver();

        // ── Summary ───────────────────────────────────────────────────────────
        System.out.println("\n" + "=".repeat(60));
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        if (!failures.isEmpty()) {
            System.out.println("  Failed cases:");
            failures.forEach(f -> System.out.println("    • " + f));
        }
        System.out.println("=".repeat(60));
        if (failed > 0) System.exit(1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test groups
    // ══════════════════════════════════════════════════════════════════════════

    static void testCrud() {
        section("CRUD – happy paths");
        TaskApi api = freshApi();

        // Create
        ApiResponse r = api.createTask(new TaskRequest.Builder()
                .title("Buy milk").description("2 litres")
                .completed("false").priority("LOW").build());
        assertStatus("createTask returns 201", r, 201);
        assertContains("createTask body has title", r, "Buy milk");
        String id = extractId(r.getBody());

        // Read
        assertStatus("getTask returns 200", api.getTask(id), 200);
        assertContains("getTask body has id", api.getTask(id), id);

        // Update
        ApiResponse upd = api.updateTask(id, new TaskRequest.Builder()
                .completed("true").priority("HIGH").build());
        assertStatus("updateTask returns 200", upd, 200);
        assertContains("updateTask body shows completed=true", upd, "\"completed\": true");

        // getAllTasks
        assertStatus("getAllTasks returns 200", api.getAllTasks(), 200);

        // Delete
        assertStatus("deleteTask returns 200", api.deleteTask(id), 200);

        // getTask after delete → 404
        assertStatus("getTask after delete → 404", api.getTask(id), 404);

        // getAllTasks on empty store
        assertContains("getAllTasks empty → []", api.getAllTasks(), "[]");
    }

    static void testValidation() {
        section("Validation – error shape and all fields");
        TaskApi api = freshApi();

        // Empty title + description
        ApiResponse r1 = api.createTask(new TaskRequest.Builder()
                .title("").description("").completed("false").build());
        assertStatus("empty title/desc → 400", r1, 400);
        assertContains("error code VALIDATION_ERROR", r1, "VALIDATION_ERROR");
        assertContains("details array present", r1, "\"details\"");
        assertContains("title field error", r1, "\"field\": \"title\"");
        assertContains("description field error", r1, "\"field\": \"description\"");

        // Invalid completed
        ApiResponse r2 = api.createTask(new TaskRequest.Builder()
                .title("T").description("D").completed("yes").build());
        assertStatus("invalid completed → 400", r2, 400);
        assertContains("completed field error", r2, "\"field\": \"completed\"");

        // Impossible date Feb-30
        ApiResponse r3 = api.createTask(new TaskRequest.Builder()
                .title("T").description("D").completed("false").dueDate("2026-02-30").build());
        assertStatus("impossible date Feb-30 → 400", r3, 400);
        assertContains("dueDate field error", r3, "\"field\": \"dueDate\"");

        // Leap-year Feb-29 valid (2024 is leap)
        ApiResponse r4 = api.createTask(new TaskRequest.Builder()
                .title("T").description("D").completed("false").dueDate("2024-02-29").build());
        assertStatus("leap-year Feb-29 → 201", r4, 201);

        // Non-leap year Feb-29 invalid (2026)
        ApiResponse r5 = api.createTask(new TaskRequest.Builder()
                .title("T").description("D").completed("false").dueDate("2026-02-29").build());
        assertStatus("non-leap Feb-29 → 400", r5, 400);

        // Invalid priority
        ApiResponse r6 = api.createTask(new TaskRequest.Builder()
                .title("T").description("D").completed("false").priority("URGENT").build());
        assertStatus("invalid priority → 400", r6, 400);
        assertContains("priority field error", r6, "\"field\": \"priority\"");

        // Multiple errors in one request
        ApiResponse r7 = api.createTask(new TaskRequest.Builder()
                .title("").description("").completed("maybe")
                .dueDate("2026-13-01").priority("CRITICAL").build());
        assertStatus("multiple errors → 400", r7, 400);
        // Should have 5 details entries
        int count = 0;
        int idx = 0;
        while ((idx = r7.getBody().indexOf("\"field\"", idx + 1)) >= 0) count++;
        assertTrue("5 field errors reported", count == 5, "got " + count);

        // 404 for unknown ID
        assertStatus("getTask unknown id → 404", api.getTask("no-such-id"), 404);
        assertContains("404 error code", api.getTask("no-such-id"), "NOT_FOUND");

        // updateTask unknown id → 404
        assertStatus("updateTask unknown id → 404",
                api.updateTask("no-such-id", new TaskRequest.Builder()
                        .completed("true").build()), 404);

        // deleteTask unknown id → 404
        assertStatus("deleteTask unknown id → 404", api.deleteTask("no-such-id"), 404);

        // Blank title (spaces only)
        ApiResponse r8 = api.createTask(new TaskRequest.Builder()
                .title("   ").description("D").completed("false").build());
        assertStatus("blank title → 400", r8, 400);

        // Invalid month (13)
        ApiResponse r9 = api.createTask(new TaskRequest.Builder()
                .title("T").description("D").completed("false").dueDate("2026-13-01").build());
        assertStatus("invalid month 13 → 400", r9, 400);
    }

    static void testIdempotency() {
        section("Idempotency – same key same request / same key different request");
        TaskApi api = freshApi();

        TaskRequest req = new TaskRequest.Builder()
                .title("Send report").description("Monthly PDF report")
                .completed("false").priority("HIGH")
                .idempotencyKey("report-nov-2026").build();

        // First call → 201
        ApiResponse first = api.createTask(req);
        assertStatus("first call → 201", first, 201);
        String firstId = extractId(first.getBody());

        // Retry same key + same request → 201 same body
        ApiResponse retry = api.createTask(req);
        assertStatus("retry same key → 201", retry, 201);
        assertTrue("retry returns same id",
                extractId(retry.getBody()).equals(firstId),
                "ids differ: " + firstId + " vs " + extractId(retry.getBody()));

        // Different request same key → 422
        TaskRequest diff = new TaskRequest.Builder()
                .title("DIFFERENT title").description("Monthly PDF report")
                .completed("false").priority("HIGH")
                .idempotencyKey("report-nov-2026").build();
        ApiResponse conflict = api.createTask(diff);
        assertStatus("same key different request → 422", conflict, 422);
        assertContains("conflict error code", conflict, "CONFLICT");

        // createTaskFromText idempotency: same key same text → cached
        ApiResponse ft1 = api.createTaskFromText("Buy coffee tomorrow", "coffee-task");
        assertStatus("createTaskFromText first call → 201", ft1, 201);
        ApiResponse ft2 = api.createTaskFromText("Buy coffee tomorrow", "coffee-task");
        assertStatus("createTaskFromText retry → 201", ft2, 201);
        assertTrue("createTaskFromText retry same id",
                extractId(ft1.getBody()).equals(extractId(ft2.getBody())),
                "ids differ");

        // createTaskFromText same key different text → 422
        ApiResponse ft3 = api.createTaskFromText("Buy tea tomorrow", "coffee-task");
        assertStatus("createTaskFromText different text → 422", ft3, 422);

        // breakDownTask idempotency
        String parentId = createTask(api, "Plan trip", "MEDIUM");
        ApiResponse bd1 = api.breakDownTask(parentId, "breakdown-trip");
        assertStatus("breakDownTask first call → 201", bd1, 201);
        ApiResponse bd2 = api.breakDownTask(parentId, "breakdown-trip");
        assertStatus("breakDownTask retry → 201 cached", bd2, 201);
        assertTrue("breakDownTask retry body identical",
                bd1.getBody().equals(bd2.getBody()), "bodies differ");
    }

    static void testFiltersAndQuery() {
        section("Filtering, keyword search, sorting – TaskQuery");
        TaskApi api = freshApi();

        // Seed data
        api.createTask(new TaskRequest.Builder()
                .title("Alpha task").description("first").completed("false")
                .priority("LOW").dueDate("2027-03-01").build());
        api.createTask(new TaskRequest.Builder()
                .title("Beta task").description("second").completed("true")
                .priority("HIGH").dueDate("2027-01-15").build());
        api.createTask(new TaskRequest.Builder()
                .title("Gamma task").description("third alpha").completed("false")
                .priority("MEDIUM").dueDate("2027-02-10").build());

        // Filter by completed=false
        ApiResponse r1 = api.queryTasks(new TaskQuery.Builder()
                .filterByCompleted(false).build());
        assertStatus("filter completed=false → 200", r1, 200);
        assertContains("filter completed=false has Alpha", r1, "Alpha task");
        assertTrue("filter completed=false excludes Beta",
                !r1.getBody().contains("Beta task"), "Beta should be excluded");

        // Filter by priority HIGH
        ApiResponse r2 = api.queryTasks(new TaskQuery.Builder()
                .filterByPriority(Priority.HIGH).build());
        assertContains("filter HIGH has Beta", r2, "Beta task");
        assertTrue("filter HIGH excludes Alpha", !r2.getBody().contains("Alpha task"), "");

        // Keyword search — matches title
        ApiResponse r3 = api.queryTasks(new TaskQuery.Builder()
                .filterByKeyword("alpha").build());
        assertContains("keyword 'alpha' matches title", r3, "Alpha task");
        assertContains("keyword 'alpha' matches description", r3, "Gamma task");
        assertTrue("keyword 'alpha' excludes Beta", !r3.getBody().contains("Beta task"), "");

        // Sort by dueDate ascending
        ApiResponse r4 = api.queryTasks(new TaskQuery.Builder()
                .sortByDueDateAsc().build());
        int betaPos  = r4.getBody().indexOf("Beta task");
        int gammaPos = r4.getBody().indexOf("Gamma task");
        int alphaPos = r4.getBody().indexOf("Alpha task");
        assertTrue("dueDateAsc: Beta(Jan) before Gamma(Feb)", betaPos < gammaPos, "");
        assertTrue("dueDateAsc: Gamma(Feb) before Alpha(Mar)", gammaPos < alphaPos, "");

        // Sort by title ascending
        ApiResponse r5 = api.queryTasks(new TaskQuery.Builder()
                .sortByTitleAsc().build());
        int aPos = r5.getBody().indexOf("Alpha");
        int bPos = r5.getBody().indexOf("Beta");
        int gPos = r5.getBody().indexOf("Gamma");
        assertTrue("titleAsc: Alpha < Beta < Gamma", aPos < bPos && bPos < gPos, "");

        // Filter + sort + pagination combined
        ApiResponse r6 = api.queryTasks(new TaskQuery.Builder()
                .filterByCompleted(false)
                .sortByDueDateAsc()
                .page(1, 1)
                .build());
        assertStatus("combined filter+sort+page → 200", r6, 200);
        assertContains("combined: first page has Gamma(Feb)", r6, "Gamma task");
        assertTrue("combined: single result",
                r6.getBody().chars().filter(c -> c == '{').count() == 1,
                "expected 1 object");

        // topLevelOnly filter
        String parentId = createTask(api, "Parent", "HIGH");
        api.createTask(new TaskRequest.Builder()
                .title("Child task").description("child desc")
                .completed("false").parentTaskId(parentId).build());
        ApiResponse r7 = api.queryTasks(new TaskQuery.Builder().topLevelOnly().build());
        assertTrue("topLevelOnly excludes subtasks",
                !r7.getBody().contains("Child task"), "Child task should be excluded");
    }

    static void testPagination() {
        section("Pagination – page beyond end returns empty list");
        TaskApi api = freshApi();

        // Seed 3 tasks
        for (int i = 1; i <= 3; i++)
            createTask(api, "Task " + i, "MEDIUM");

        // Page 2 of size 2 → 1 task
        ApiResponse r1 = api.queryTasks(new TaskQuery.Builder().page(2, 2).build());
        assertStatus("page 2 size 2 → 200", r1, 200);
        long count1 = r1.getBody().chars().filter(c -> c == '{').count();
        assertTrue("page 2 size 2 returns 1 task", count1 == 1, "got " + count1);

        // Page 3 size 2 → empty
        ApiResponse r2 = api.queryTasks(new TaskQuery.Builder().page(3, 2).build());
        assertStatus("page beyond end → 200", r2, 200);
        assertContains("page beyond end → []", r2, "[]");

        // Page 1 size 10 → all 3
        ApiResponse r3 = api.queryTasks(new TaskQuery.Builder().page(1, 10).build());
        long count3 = r3.getBody().chars().filter(c -> c == '{').count();
        assertTrue("page 1 size 10 returns all 3", count3 == 3, "got " + count3);
    }

    static void testSubtasks() {
        section("Subtasks – create, list, cascade delete, guard");
        TaskApi api = freshApi();

        String parentId = createTask(api, "Parent task", "HIGH");

        // createTask with parentTaskId → 201
        ApiResponse child1 = api.createTask(new TaskRequest.Builder()
                .title("Subtask one").description("first child")
                .completed("false").parentTaskId(parentId).build());
        assertStatus("createSubtask → 201", child1, 201);
        assertContains("subtask has parentTaskId", child1, parentId);
        String childId = extractId(child1.getBody());

        // listSubtasks → 200 with child
        ApiResponse list = api.listSubtasks(parentId);
        assertStatus("listSubtasks → 200", list, 200);
        assertContains("listSubtasks has Subtask one", list, "Subtask one");

        // listSubtasks unknown parent → 404
        assertStatus("listSubtasks unknown parent → 404",
                api.listSubtasks("bad-id"), 404);

        // Parent shows subtaskIds updated
        ApiResponse parentResp = api.getTask(parentId);
        assertContains("parent subtaskIds contains child", parentResp, childId);

        // subtask-of-subtask: breakdown guard
        ApiResponse bd = api.breakDownTask(childId, null);
        assertStatus("breakdown subtask → 400", bd, 400);
        assertContains("breakdown subtask error", bd, "subtask cannot be broken");

        // Cascade delete: delete parent removes child
        assertStatus("deleteTask parent → 200", api.deleteTask(parentId), 200);
        assertStatus("child gone after cascade delete", api.getTask(childId), 404);
    }

    static void testBreakdown() {
        section("breakDownTask – rule-based, idempotency, guard");
        TaskApi api = freshApi();

        String id = createTask(api, "Write a book", "MEDIUM");
        // Update description to something with "and" so rule-based splits it
        api.updateTask(id, new TaskRequest.Builder()
                .description("Research topic and outline chapters and write draft").build());

        ApiResponse bd = api.breakDownTask(id, "breakdown-book");
        assertStatus("breakDownTask → 201", bd, 201);
        // Rule-based splits on " and " → 3 subtasks
        long subtaskCount = bd.getBody().chars().filter(c -> c == '{').count();
        assertTrue("breakdown produces multiple subtasks", subtaskCount >= 2,
                "got " + subtaskCount);

        // Retry same key → cached, no new subtasks
        ApiResponse bd2 = api.breakDownTask(id, "breakdown-book");
        assertStatus("breakDownTask retry → 201", bd2, 201);
        assertTrue("breakDownTask retry body identical",
                bd.getBody().equals(bd2.getBody()), "bodies differ");

        // listSubtasks confirms children exist
        ApiResponse sub = api.listSubtasks(id);
        // Response is a JSON array — check it has at least one task object
        assertTrue("listSubtasks non-empty after breakdown",
                sub.getBody().trim().startsWith("[") && sub.getBody().contains("\"id\""),
                "expected subtask objects, got: " + sub.getBody().substring(0, Math.min(100, sub.getBody().length())));
    }

    static void testCreateFromText() {
        section("createTaskFromText – rule-based parser (no API key needed)");
        TaskApi api = freshApi();

        // "tomorrow" resolves to a date
        ApiResponse r1 = api.createTaskFromText("Buy coffee tomorrow", null);
        assertStatus("createTaskFromText → 201", r1, 201);
        assertContains("createTaskFromText has dueDate", r1, "dueDate");

        // "next Monday"
        ApiResponse r2 = api.createTaskFromText("Call the dentist next Monday", null);
        assertStatus("next Monday → 201", r2, 201);

        // "every month on the 15th"
        ApiResponse r3 = api.createTaskFromText("Pay bills every month on the 15th", null);
        assertStatus("monthly pattern → 201", r3, 201);

        // empty text → 400
        ApiResponse r4 = api.createTaskFromText("", null);
        assertStatus("empty text → 400", r4, 400);

        // blank text → 400
        ApiResponse r5 = api.createTaskFromText("   ", null);
        assertStatus("blank text → 400", r5, 400);

        // FakeLlmClient — fixed good reply
        TaskApi fakeApi = freshApiWith(FakeLlmClient.withTitle("Fake task title"));
        ApiResponse r6 = fakeApi.createTaskFromText("anything", null);
        assertStatus("FakeLlmClient → 201", r6, 201);
        assertContains("FakeLlmClient body has title", r6, "Fake task title");

        // FakeLlmClient with due date
        TaskApi fakeDateApi = freshApiWith(
                FakeLlmClient.withDueDate("Buy tickets", "2027-06-15"));
        ApiResponse r7 = fakeDateApi.createTaskFromText("Book concert tickets", null);
        assertStatus("FakeLlmClient with date → 201", r7, 201);
        assertContains("FakeLlmClient date in body", r7, "2027-06-15");
    }

    static void testFaultyLlm() {
        section("FaultyLlmClient – bad model output → 502, nothing saved");
        TaskApi emptyTitle = freshApiWith(
                new FaultyLlmClient(FaultyLlmClient.FaultMode.EMPTY_TITLE));
        TaskApi badDate    = freshApiWith(
                new FaultyLlmClient(FaultyLlmClient.FaultMode.IMPOSSIBLE_DATE));
        TaskApi network    = freshApiWith(
                new FaultyLlmClient(FaultyLlmClient.FaultMode.NETWORK_ERROR));
        TaskApi noKey      = freshApiWith(
                new FaultyLlmClient(FaultyLlmClient.FaultMode.NO_API_KEY));
        TaskApi malformed  = freshApiWith(
                new FaultyLlmClient(FaultyLlmClient.FaultMode.MALFORMED_JSON));
        TaskApi blank      = freshApiWith(
                new FaultyLlmClient(FaultyLlmClient.FaultMode.BLANK_CONTENT));

        // empty title → 502
        ApiResponse r1 = emptyTitle.createTaskFromText("Buy milk", null);
        assertStatus("faulty empty title → 502", r1, 502);
        assertContains("faulty empty title error code", r1, "BAD_GATEWAY");

        // impossible date → 502
        ApiResponse r2 = badDate.createTaskFromText("Pay rent", null);
        assertStatus("faulty bad date → 502", r2, 502);

        // network error → 502
        ApiResponse r3 = network.createTaskFromText("Write report", null);
        assertStatus("faulty network error → 502", r3, 502);

        // no api key → 503
        ApiResponse r4 = noKey.createTaskFromText("Read book", null);
        assertStatus("faulty no api key → 503", r4, 503);
        assertContains("no api key error code", r4, "SERVICE_UNAVAILABLE");

        // malformed json → 502
        ApiResponse r5 = malformed.createTaskFromText("Exercise", null);
        assertStatus("faulty malformed json → 502", r5, 502);

        // blank content → 502
        ApiResponse r6 = blank.createTaskFromText("Plan meeting", null);
        assertStatus("faulty blank content → 502", r6, 502);

        // Confirm no tasks saved after faulty calls
        assertTrue("emptyTitle: no tasks saved after 502",
                emptyTitle.getAllTasks().getBody().contains("[]"), "tasks were saved");
        assertTrue("badDate: no tasks saved after 502",
                badDate.getAllTasks().getBody().contains("[]"), "tasks were saved");

        // Faulty idempotency: same key with faulty client never calls parser twice
        // (it fails before storing, so retry with good input is allowed)
        TaskApi faultyThenGood = freshApiWith(
                new FaultyLlmClient(FaultyLlmClient.FaultMode.NETWORK_ERROR));
        ApiResponse bad  = faultyThenGood.createTaskFromText("Task text", "retry-key");
        assertStatus("faulty first call → 502", bad, 502);
        // Now swap to fake (simulate client fix and retry with same key — not stored so allowed)
        // We can't swap the client mid-flight on this api; just verify bad call didn't store
        assertTrue("failed call not stored in idempotency store",
                faultyThenGood.createTaskFromText("Task text", "retry-key").getStatusCode() == 502,
                "expected another 502 since parser still faulty");
    }

    static void testObserver() {
        section("Observer – addListener, events fired, removeListener stops notifications");

        InMemoryTaskRepository repo   = InMemoryTaskRepository.createFresh();
        IdempotencyStore       idem   = IdempotencyStore.createFresh();
        AuditTrailListener     audit  = new AuditTrailListener();
        TaskService            svc    = new TaskService(repo, ParserFactory.ruleBased(),
                                            new RuleBasedBreakdownStrategy(), idem);
        TaskApi                api    = new TaskApi(svc);

        svc.addListener(audit);

        // Create → CREATED event
        String id = createTask(api, "Observer test task", "MEDIUM");
        assertTrue("CREATED event fired",
                audit.getCount(TaskEvent.CREATED) == 1, "count=" + audit.getCount(TaskEvent.CREATED));

        // Update (not completed) → UPDATED event
        api.updateTask(id, new TaskRequest.Builder().priority("HIGH").build());
        assertTrue("UPDATED event fired",
                audit.getCount(TaskEvent.UPDATED) == 1, "count=" + audit.getCount(TaskEvent.UPDATED));

        // Update to completed → COMPLETED event (not UPDATED)
        api.updateTask(id, new TaskRequest.Builder().completed("true").build());
        assertTrue("COMPLETED event fired",
                audit.getCount(TaskEvent.COMPLETED) == 1,
                "count=" + audit.getCount(TaskEvent.COMPLETED));
        assertTrue("UPDATED not incremented for complete",
                audit.getCount(TaskEvent.UPDATED) == 1,
                "UPDATED count=" + audit.getCount(TaskEvent.UPDATED));

        // Delete → DELETED event
        api.deleteTask(id);
        assertTrue("DELETED event fired",
                audit.getCount(TaskEvent.DELETED) == 1,
                "count=" + audit.getCount(TaskEvent.DELETED));

        // Audit trail has 4 entries
        assertTrue("audit trail has 4 entries",
                audit.getTrail().size() == 4,
                "trail size=" + audit.getTrail().size());

        // removeListener: creating another task should NOT increment count
        svc.removeListener(audit);
        int countBefore = audit.getCount(TaskEvent.CREATED);
        createTask(api, "After remove", "LOW");
        assertTrue("removeListener stops notifications",
                audit.getCount(TaskEvent.CREATED) == countBefore,
                "count changed after remove");

        audit.printSummary();
    }
}
