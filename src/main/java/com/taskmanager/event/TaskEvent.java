package com.taskmanager.event;

/**
 * Enum of all task lifecycle events that TaskService can announce.
 */
public enum TaskEvent {
    CREATED,
    UPDATED,
    COMPLETED,
    DELETED
}
