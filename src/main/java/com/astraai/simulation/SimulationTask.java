package com.astraai.simulation;

import java.util.Objects;

/** Immutable task definition, independent of assignment and execution state. */
public record SimulationTask(TaskId id, CandidateRange candidateRange) {
    public SimulationTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(candidateRange, "candidateRange");
    }
}
