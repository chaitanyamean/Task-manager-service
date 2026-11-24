package com.taskmanager.service;

import com.taskmanager.api.ApiResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe Singleton that tracks idempotency keys for safe retries.
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li>Same key + same fingerprint → return the stored response (no duplicate work).</li>
 *   <li>Same key + different fingerprint → callers must return 422 CONFLICT.</li>
 *   <li>Only successful responses are stored, so a client can fix bad input and retry
 *       with the same key.</li>
 * </ul>
 *
 * <h3>Fingerprinting</h3>
 * The fingerprint is computed from all request field values joined into one String.
 * Any change in the request (different title, different date, etc.) produces a
 * different fingerprint and triggers a 422.
 *
 * <h3>Thread safety</h3>
 * All public methods are {@code synchronized}. If the application later moves to a
 * multi-threaded environment the store remains safe without further changes.
 */
public final class IdempotencyStore {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static IdempotencyStore instance;

    public static synchronized IdempotencyStore getInstance() {
        if (instance == null) instance = new IdempotencyStore();
        return instance;
    }

    /** Private constructor for Singleton. Use getInstance() normally. */
    private IdempotencyStore() {}

    /**
     * Creates a fresh, independent instance for testing.
     * Tests should use this instead of the Singleton to avoid shared state.
     */
    public static IdempotencyStore createFresh() {
        return new IdempotencyStore();
    }

    // ── Internal record ───────────────────────────────────────────────────────

    private static final class Record {
        final String      fingerprint;
        final ApiResponse response;

        Record(String fingerprint, ApiResponse response) {
            this.fingerprint = fingerprint;
            this.response    = response;
        }
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    private final Map<String, Record> store = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Looks up a key.
     *
     * @return {@link LookupResult} describing whether the key is new, matches, or conflicts.
     */
    public synchronized LookupResult lookup(String key, String fingerprint) {
        if (key == null || key.isBlank()) return LookupResult.newKey();

        Record existing = store.get(key);
        if (existing == null) return LookupResult.newKey();

        if (existing.fingerprint.equals(fingerprint)) {
            return LookupResult.hit(existing.response);
        }
        return LookupResult.conflict();
    }

    /**
     * Stores a successful response under the given key.
     * No-op if key is null or blank.
     */
    public synchronized void store(String key, String fingerprint, ApiResponse response) {
        if (key == null || key.isBlank()) return;
        store.put(key, new Record(fingerprint, response));
    }

    /** Removes all entries — useful for testing. */
    public synchronized void clear() {
        store.clear();
    }

    /** Returns the number of stored entries. */
    public synchronized int size() {
        return store.size();
    }

    // ── LookupResult ─────────────────────────────────────────────────────────

    public static final class LookupResult {
        public enum Status { NEW, HIT, CONFLICT }

        private final Status      status;
        private final ApiResponse response; // non-null only for HIT

        private LookupResult(Status status, ApiResponse response) {
            this.status   = status;
            this.response = response;
        }

        public static LookupResult newKey()              { return new LookupResult(Status.NEW, null); }
        public static LookupResult hit(ApiResponse resp) { return new LookupResult(Status.HIT, resp); }
        public static LookupResult conflict()            { return new LookupResult(Status.CONFLICT, null); }

        public boolean isNew()      { return status == Status.NEW; }
        public boolean isHit()      { return status == Status.HIT; }
        public boolean isConflict() { return status == Status.CONFLICT; }

        public Optional<ApiResponse> getResponse() { return Optional.ofNullable(response); }
    }

    // ── Fingerprint helper ────────────────────────────────────────────────────

    /**
     * Builds a fingerprint from an arbitrary number of string values.
     * Joining with a separator that cannot appear in normal field values keeps
     * "ab" + "cd" distinct from "a" + "bcd".
     */
    public static String fingerprint(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            sb.append(v == null ? "<null>" : v);
            sb.append('\u001F'); // ASCII Unit Separator — not in user text
        }
        return sb.toString();
    }
}
