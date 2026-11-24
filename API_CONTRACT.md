# API Contract — Task Manager Service

This document is the authoritative contract for every `TaskApi` operation.  
If the service is later exposed over HTTP, this becomes the OpenAPI specification.

---

## Base

All operations are Java method calls on `TaskApi`. Each returns an `ApiResponse` containing:
- `statusCode` — integer HTTP-style status code
- `body` — UTF-8 JSON string

---

## Error Response Shape

Every error — regardless of operation — uses this exact shape.  
`code` and `message` are always strings.

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable description",
    "details": [
      { "field": "fieldName", "issue": "what is wrong" }
    ]
  }
}
```

`details` is present only for `VALIDATION_ERROR`. It is omitted for all other error codes.

### Error Codes

| Code | HTTP Status | Meaning |
|------|-------------|---------|
| `VALIDATION_ERROR` | 400 | One or more request fields failed validation |
| `NOT_FOUND` | 404 | The requested resource does not exist |
| `CONFLICT` | 422 | Idempotency key reused with a different request |
| `BAD_GATEWAY` | 502 | LLM call failed, or LLM returned invalid data |
| `SERVICE_UNAVAILABLE` | 503 | `LLM_API_KEY` environment variable is not set |
| `INTERNAL_ERROR` | 500 | Unexpected error — should not occur in normal use |

---

## Validation Rules

Applied to `TaskRequest` fields before any task is created or updated.

| Field | Rule |
|-------|------|
| `title` | Required on create. Must not be null, empty, or blank. |
| `description` | Required on create. Must not be null, empty, or blank. |
| `completed` | Required on create. Must be exactly the string `"true"` or `"false"`. |
| `dueDate` | Optional. If present, must be a real calendar date in `YYYY-MM-DD` format. Impossible dates (e.g. `2026-02-30`) are rejected. Leap-year rules apply. |
| `priority` | Optional. If present, must be exactly `LOW`, `MEDIUM`, or `HIGH` (case-insensitive). Defaults to `MEDIUM` if omitted. |
| `parentTaskId` | Optional. If present, the referenced task must exist. |
| `idempotencyKey` | Optional. See [Idempotency](#idempotency) below. |

All errors are collected in one pass and returned together in `details`.

---

## Operations

---

### `createTask(TaskRequest request)`

Creates a new task.

**Success:** `201 Created`

```json
{
  "id": "7588f7b6-3fa6-460a-b641-6ca9cf4c2a50",
  "title": "Pay rent",
  "description": "Bank transfer on the 1st",
  "completed": false,
  "dueDate": "2026-12-01",
  "priority": "HIGH",
  "parentTaskId": null,
  "subtaskIds": [],
  "idempotencyKey": "pay-rent-dec-2026"
}
```

**Error responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 400 | `VALIDATION_ERROR` | Any field fails a validation rule |
| 404 | `NOT_FOUND` | `parentTaskId` refers to a non-existent task |
| 422 | `CONFLICT` | `idempotencyKey` was already used with a different request |

---

### `createTaskFromText(String text, String idempotencyKey)`

Converts a plain-English description into a task using the configured `TaskParser`.

The parser output is always validated with the same rules as `createTask` before saving.  
If the LLM returns invalid data, the operation returns `502` and nothing is stored.

**Success:** `201 Created` — same body shape as `createTask`

**Error responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 400 | `VALIDATION_ERROR` | `text` is null, empty, or blank |
| 422 | `CONFLICT` | `idempotencyKey` reused with different text |
| 502 | `BAD_GATEWAY` | LLM call failed, timed out, or returned invalid/empty data |
| 503 | `SERVICE_UNAVAILABLE` | `LLM_API_KEY` environment variable is not set |

---

### `getTask(String id)`

Returns the task with the given ID.

**Success:** `200 OK` — task object (same shape as `createTask` response)

**Error responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 404 | `NOT_FOUND` | No task with that ID exists |

---

### `getAllTasks()`

Returns all tasks with no filters, sorting, or pagination.

**Success:** `200 OK`

```json
[
  { "id": "...", "title": "Task A", ... },
  { "id": "...", "title": "Task B", ... }
]
```

Returns `[]` if no tasks exist. Never returns an error.

---

### `getAllTasks(String completed, String priority, String sortBy, int page, int pageSize)`

Returns tasks with optional filtering, sorting, and pagination.

| Parameter | Values | Effect |
|-----------|--------|--------|
| `completed` | `"true"` / `"false"` / `null` | Filter by completion status |
| `priority` | `"LOW"` / `"MEDIUM"` / `"HIGH"` / `null` | Filter by priority |
| `sortBy` | `"dueDate"` / `"priority"` / `null` | Sort order |
| `page` | integer ≥ 1 (or ≤ 0 to disable) | Page number (1-based) |
| `pageSize` | integer ≥ 1 (or ≤ 0 to disable) | Items per page |

**Success:** `200 OK` — JSON array (may be `[]` if page is beyond the last result)

---

### `queryTasks(TaskQuery query)`

Applies a composable `TaskQuery` (filters + sort + pagination) in one call.  
This is the recommended way to combine multiple constraints.

```java
TaskQuery query = new TaskQuery.Builder()
    .filterByCompleted(false)
    .filterByPriority(Priority.HIGH)
    .filterByKeyword("rent")
    .sortByDueDateAsc()
    .page(1, 10)
    .build();
ApiResponse r = api.queryTasks(query);
```

**Available filters:** `ByCompleted`, `ByPriority`, `ByKeyword`, `TopLevelOnly`, `SubtasksOnly`, `DueBefore`  
**Available sorts:** `ByDueDateAsc`, `ByDueDateDesc`, `ByPriorityDesc`, `ByPriorityAsc`, `ByTitleAsc`, `ByTitleDesc`, `Chained`

**Success:** `200 OK` — JSON array (may be `[]`)

---

### `updateTask(String id, TaskRequest request)`

Partially updates an existing task. Only non-null fields in the request are applied (PATCH semantics). The `idempotencyKey` and `parentTaskId` fields cannot be changed after creation.

**Success:** `200 OK` — updated task object

**Error responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 400 | `VALIDATION_ERROR` | A provided field fails validation |
| 404 | `NOT_FOUND` | No task with that ID exists |

---

### `deleteTask(String id)`

Deletes a task and all its direct subtasks (cascade delete).

**Success:** `200 OK`

```json
{ "message": "Task and its subtasks deleted successfully" }
```

**Error responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 404 | `NOT_FOUND` | No task with that ID exists |

---

### `listSubtasks(String parentId)`

Returns the direct subtasks of the task with the given ID.

**Success:** `200 OK` — JSON array of task objects (may be `[]`)

**Error responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 404 | `NOT_FOUND` | No task with that `parentId` exists |

---

### `breakDownTask(String id, String idempotencyKey)`

Uses the configured `BreakdownStrategy` to propose subtasks for the given task, validates every proposal, then saves them all atomically (all-or-nothing).

**Constraints:**
- The task must be a top-level task. Subtasks cannot be broken down further (→ 400).
- If any proposed subtask fails validation, nothing is saved (→ 502).
- If the strategy call fails, nothing is saved (→ 502).

**Success:** `201 Created` — JSON array of created subtask objects

**Error responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 400 | `VALIDATION_ERROR` | `id` refers to a subtask (cannot break down) |
| 404 | `NOT_FOUND` | No task with that ID exists |
| 422 | `CONFLICT` | `idempotencyKey` reused with a different `id` |
| 502 | `BAD_GATEWAY` | Strategy call failed or returned invalid subtask data |
| 503 | `SERVICE_UNAVAILABLE` | `LLM_API_KEY` not set (LLM strategy only) |

---

## Idempotency

Any operation that creates data accepts an optional `idempotencyKey` string.

| Scenario | Behaviour |
|----------|-----------|
| New key | Operation executes normally. Response stored under the key. |
| Same key + same request | Stored response returned immediately. No work done, no duplicate created. |
| Same key + different request | `422 CONFLICT` returned. Nothing is created or changed. |
| Failed request (4xx/5xx) | Response is **not** stored. Client may fix the request and retry with the same key. |

The fingerprint is computed from all request field values. A single character change produces a different fingerprint and triggers a conflict.

---

## Task Object Shape

```json
{
  "id":             "7588f7b6-3fa6-460a-b641-6ca9cf4c2a50",
  "title":          "Pay rent",
  "description":    "Bank transfer on the 1st of every month",
  "completed":      false,
  "dueDate":        "2026-12-01",
  "priority":       "HIGH",
  "parentTaskId":   null,
  "subtaskIds":     ["abc123...", "def456..."],
  "idempotencyKey": "pay-rent-dec-2026"
}
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | string (UUID) | Assigned by the service; immutable |
| `title` | string | Non-empty |
| `description` | string | Non-empty |
| `completed` | boolean | `false` on creation unless explicitly set |
| `dueDate` | string \| null | `YYYY-MM-DD` or `null` |
| `priority` | string | `"LOW"` / `"MEDIUM"` / `"HIGH"` |
| `parentTaskId` | string \| null | ID of parent task, or `null` for top-level tasks |
| `subtaskIds` | array of strings | IDs of direct child tasks |
| `idempotencyKey` | string \| null | The key used at creation, or `null` |
