package com.astraai.simulation;

/** Categories exposed by the engine's bounded observer stream. */
public enum EngineEventType {
    INITIAL_SNAPSHOT,
    STATE_CHANGED,
    PROGRESS,
    RESULT_VALIDATED,
    CLEANUP_COMPLETED,
    WORKER_FAILURE,
    RESYNC_REQUIRED
}
