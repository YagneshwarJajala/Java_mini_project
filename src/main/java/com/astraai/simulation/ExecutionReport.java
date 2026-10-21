package com.astraai.simulation;

import java.util.List;
import java.util.Objects;

/** Immutable terminal view of bounded candidate execution and its validation. */
public record ExecutionReport(SimulationResult result, ValidationReport validation,
        long completedEvaluations, List<AgentSnapshot> agents, List<TaskSnapshot> tasks) {
    public ExecutionReport {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(validation, "validation");
        agents = List.copyOf(Objects.requireNonNull(agents, "agents"));
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        if (completedEvaluations < 0) {
            throw new IllegalArgumentException("completedEvaluations cannot be negative");
        }
        EvaluationEvidence evidence = result.evaluationEvidence().orElseThrow(
                () -> new IllegalArgumentException("execution reports require per-candidate evaluation evidence"));
        if (completedEvaluations != evidence.totalCompletedEvaluations()) {
            throw new IllegalArgumentException("completedEvaluations must match the result evidence total");
        }
        long agentCompletedEvaluations = agents.stream().mapToLong(AgentSnapshot::completedEvaluations).sum();
        if (completedEvaluations != agentCompletedEvaluations) {
            throw new IllegalArgumentException("completedEvaluations must match the logical-agent totals");
        }
        if (!validation.valid()) {
            throw new IllegalArgumentException("execution reports must contain a structurally valid result");
        }
    }
}
