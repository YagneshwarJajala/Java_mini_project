package com.astraai.simulation;

/** Publicly observable task states; QUEUED means unassigned in the task manager. */
public enum TaskState {
    QUEUED,
    ASSIGNED,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}
