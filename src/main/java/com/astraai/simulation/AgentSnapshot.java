package com.astraai.simulation;

import java.util.Objects;
import java.util.Optional;

/** Immutable observable state for one logical agent; it is not a worker-thread record. */
public record AgentSnapshot(AgentId id, AgentState state, Optional<TaskId> currentTask,
        long completedEvaluations) {
    public AgentSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currentTask, "currentTask");
        if (completedEvaluations < 0) {
            throw new IllegalArgumentException("completedEvaluations cannot be negative");
        }
        if ((state == AgentState.WAITING || state == AgentState.WORKING) && currentTask.isEmpty()) {
            throw new IllegalArgumentException("active agent must have an assigned task");
        }
    }
}
