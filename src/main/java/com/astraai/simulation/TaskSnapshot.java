package com.astraai.simulation;

import java.util.Objects;
import java.util.Optional;

/** Immutable public view of task definition, state, and current/retained owner. */
public record TaskSnapshot(
        TaskId id,
        CandidateRange candidateRange,
        TaskState state,
        Optional<AgentId> owner) {

    public TaskSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(candidateRange, "candidateRange");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(owner, "owner");
        if (state == TaskState.QUEUED && owner.isPresent()) {
            throw new IllegalArgumentException("unassigned task cannot have an owner");
        }
        if ((state == TaskState.ASSIGNED || state == TaskState.RUNNING || state == TaskState.PAUSED
                || state == TaskState.COMPLETED) && owner.isEmpty()) {
            throw new IllegalArgumentException("assigned or completed task must retain its owner");
        }
    }
}
