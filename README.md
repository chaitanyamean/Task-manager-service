# Task Manager Service

A plain-Java task manager that uses an LLM to convert natural-language input into structured tasks, supports subtask hierarchies, safe retries via idempotency, rich filtering/sorting/pagination, and event-driven notifications — all without a web framework or external libraries.

---

## Package Structure

```
src/main/java/com/taskmanager/
├── app/
│   ├── Main.java                  Entry point; demonstrates every API operation
│   └── TestRunner.java            Plain-Java test runner; prints PASS / FAIL
├── api/
│   ├── ApiResponse.java           HTTP-style response wrapper (status + JSON body)
│   ├── TaskApi.java               Public API layer; every method returns ApiResponse
│   └── ErrorResponseFactory.java  Single place for the mandated error JSON shape
├── model/
│   ├── Task.java                  Immutable task model (Builder pattern)
│   ├── TaskRequest.java           Raw string input carrier (Builder pattern)
│   └── Priority.java              LOW / MEDIUM / HIGH enum
├── service/
│   ├── TaskService.java           Business logic; fires Observer events
│   ├── TaskQuery.java             Composable query object (Builder + Strategy)
│   ├── TaskFilter.java            Filter strategy interface + 6 implementations
│   ├── SortStrategy.java          Sort strategy interface + 7 implementations
│   ├── BreakdownStrategy.java     Subtask breakdown interface (Strategy)
│   ├── LlmBreakdownStrategy.java  LLM-backed breakdown
│   ├── RuleBasedBreakdownStrategy.java  Offline breakdown (split on "and"/commas)
│   └── IdempotencyStore.java      Thread-safe Singleton; fingerprint + response cache
├── repository/
│   ├── TaskRepository.java        Persistence interface (Dependency Inversion)
│   └── InMemoryTaskRepository.java  Singleton HashMap store
├── parser/
│   ├── TaskParser.java            Strategy interface: text + today → ParsedTask
│   ├── ParsedTask.java            Holds title, description, dueDate, recurrence
│   ├── LlmTaskParser.java         Sends prompt to LLM; parses JSON reply
│   ├── RuleBasedTaskParser.java   Offline: tomorrow / next Monday / 1st / every month
│   ├── ParserFactory.java         Returns LLM parser if key set, rule-based otherwise
│   ├── LlmClient.java             Thin HTTP client → Anthropic (reads LLM_API_KEY)
│   ├── SimpleJson.java            Flat JSON reader (no library)
│   ├── FakeLlmClient.java         Test double: fixed reply, no network
│   └── FaultyLlmClient.java       Test double: 6 fault modes for error-path testing
├── event/
│   ├── TaskEvent.java             CREATED / UPDATED / COMPLETED / DELETED enum
│   ├── TaskEventListener.java     Observer interface: onEvent(event, task)
│   ├── ConsoleLogListener.java    Prints one log line per event
│   └── AuditTrailListener.java    Counts events; keeps full ordered trail
└── exception/
    ├── ApiException.java          Base: httpStatus + errorCode
    ├── ValidationException.java   400; collects multiple FieldErrors
    ├── NotFoundException.java     404
    ├── ConflictException.java     422
    ├── BadGatewayException.java   502
    └── ServiceUnavailableException.java  503
```

---

## Setup

### Prerequisites
- **JDK 17** or newer (`java --version` to verify)
- An IDE (IntelliJ IDEA or Cursor recommended)
- A GitHub account to push the repo

### Compile

```bash
# From the project root
mkdir -p out
javac --release 17 -d out $(find src -name "*.java")
```

### Run the demo

```bash
java -cp out com.taskmanager.app.Main
```

### Run the test suite

```bash
java -cp out com.taskmanager.app.TestRunner
```

All tests pass without a real API key or internet connection. FakeLlmClient and FaultyLlmClient are used for all LLM-dependent cases.

### Enable AI features (optional)

```bash
export LLM_API_KEY=sk-ant-...          # Anthropic Claude
java -cp out com.taskmanager.app.Main
```

The key is read from the environment at runtime — **never commit it**. The `.gitignore` excludes `.env` files. If the key is absent, `createTaskFromText` and `breakDownTask` (LLM variant) return **503 SERVICE_UNAVAILABLE** instead of crashing.

---

## TaskApi Operations

| Method | Parameters | Success | Error statuses |
|--------|-----------|---------|----------------|
| `createTask(request)` | TaskRequest | 201 | 400, 422 |
| `createTaskFromText(text, key)` | String text, String idempotencyKey | 201 | 400, 422, 502, 503 |
| `getTask(id)` | String id | 200 | 404 |
| `getAllTasks()` | — | 200 | — |
| `getAllTasks(completed, priority, sort, page, size)` | filters + sort + pagination | 200 | — |
| `queryTasks(query)` | TaskQuery | 200 | — |
| `updateTask(id, request)` | String id, TaskRequest | 200 | 400, 404 |
| `deleteTask(id)` | String id | 200 | 404 |
| `listSubtasks(parentId)` | String parentId | 200 | 404 |
| `breakDownTask(id, key)` | String id, String idempotencyKey | 201 | 400, 422, 502, 503 |

### Example: create a task

```java
ApiResponse r = api.createTask(new TaskRequest.Builder()
    .title("Pay rent")
    .description("Bank transfer on the 1st")
    .completed("false")
    .priority("HIGH")
    .dueDate("2026-12-01")
    .idempotencyKey("pay-rent-dec-2026")
    .build());
// HTTP 201
// { "id": "...", "title": "Pay rent", "completed": false, ... }
```

### Example: AI task creation

```java
ApiResponse r = api.createTaskFromText(
    "Remind me to call the dentist next Monday", null);
// HTTP 201
// { "id": "...", "title": "Call the dentist next Monday", "dueDate": "2026-11-30", ... }
```

### Example: validation error

```java
ApiResponse r = api.createTask(new TaskRequest.Builder()
    .title("").description("").completed("maybe")
    .dueDate("2026-02-30").priority("URGENT").build());
// HTTP 400
// {
//   "error": {
//     "code": "VALIDATION_ERROR",
//     "message": "Validation failed",
//     "details": [
//       { "field": "title",       "issue": "must not be empty" },
//       { "field": "description", "issue": "must not be empty" },
//       { "field": "completed",   "issue": "must be exactly \"true\" or \"false\"" },
//       { "field": "dueDate",     "issue": "must be a valid date in YYYY-MM-DD format" },
//       { "field": "priority",    "issue": "must be LOW, MEDIUM, or HIGH" }
//     ]
//   }
// }
```

### Example: query with filters, sort, and pagination

```java
ApiResponse r = api.queryTasks(new TaskQuery.Builder()
    .filterByCompleted(false)
    .filterByPriority(Priority.HIGH)
    .filterByKeyword("rent")
    .sortByDueDateAsc()
    .page(1, 10)
    .build());
// HTTP 200  — JSON array of matching tasks
```

### Example: breakdown

```java
ApiResponse r = api.breakDownTask(parentId, "breakdown-trip-2026");
// HTTP 201  — JSON array of created subtasks
```

---

## Design Patterns Used

| Pattern | Where |
|---------|-------|
| **Builder** | `Task`, `TaskRequest`, `ParsedTask`, `TaskQuery` — fluent construction, all fields optional except required ones |
| **Singleton** | `InMemoryTaskRepository.getInstance()`, `IdempotencyStore.getInstance()` — one shared store per JVM |
| **Factory** | `ParserFactory.create()` — selects LLM or rule-based parser based on environment; `ErrorResponseFactory.from()` — builds error JSON in one place |
| **Strategy** | `TaskParser` (LlmTaskParser / RuleBasedTaskParser), `BreakdownStrategy` (LlmBreakdownStrategy / RuleBasedBreakdownStrategy), `TaskFilter` (6 implementations), `SortStrategy` (7 implementations) — swap algorithms without touching service code |
| **Observer** | `TaskEventListener` / `TaskService.addListener` / `removeListener` — ConsoleLogListener and AuditTrailListener react to task lifecycle events without coupling to TaskService |

---

## LLM Prompts

### Task creation prompt (`LlmTaskParser`)

```
Today's date is <YYYY-MM-DD>.

Convert the following plain-English task description into a JSON object.
Reply with ONLY the JSON object — no explanation, no markdown, no code fences.

Required fields:
  "title"       – a short, imperative task title (non-empty string)
  "description" – one sentence explaining what needs to be done (non-empty string)

Optional fields (use JSON null if not applicable):
  "dueDate"    – the resolved due date in YYYY-MM-DD format, or null
  "recurrence" – one of: daily, weekly, monthly, yearly, or null

Task description:
"<user text>"

JSON:
```

**Why this way:** Including today's date lets the model resolve relative phrases ("next Friday", "in 3 days") to concrete dates. Asking for JSON-only prevents prose wrapping that breaks `SimpleJson`. The `JSON:` suffix primes the model to output the object immediately. Restricting `recurrence` to four values keeps downstream code simple.

### Breakdown prompt (`LlmBreakdownStrategy`)

```
Break down the following task into 3-5 concrete subtasks.
Reply with ONLY the subtasks — one per line, no numbering, no extra text.
Each line must follow EXACTLY this format:  title|description

Task title: <title>
Task description: <description>

Subtasks:
```

**Why this way:** The `title|description` pipe format is unambiguous and trivially split with `String.indexOf('|')` — no JSON library needed. Fixing the count to 3-5 avoids single-item or runaway responses. The `Subtasks:` suffix primes the model to continue the list directly.

---

## AI-Generated Tests: Critique and Changes

The AI initially generated tests for the basic CRUD happy paths (create, read, update, delete, 404). The following gaps were identified and fixed:

| Gap | Fix |
|-----|-----|
| No idempotency conflict test (same key, different request) | Added `testIdempotency` → 422 path |
| No faulty LLM tests | Added `testFaultyLlm` with all 6 `FaultMode` values |
| No subtask-of-subtask breakdown guard | Added to `testSubtasks` → 400 path |
| No page-beyond-last-page test | Added to `testPagination` → 200 with `[]` body |
| No keyword search test | Added to `testFiltersAndQuery` |
| No multi-field validation error count check | Added `count == 5` assertion in `testValidation` |
| No listener remove test | Added to `testObserver` |
| No audit trail count verification | Added per-event count checks in `testObserver` |
| No `createTaskFromText` empty/blank text test | Added to `testCreateFromText` |
| Leap-year boundary not tested | Added Feb-29 valid (2024) and invalid (2026) cases |
