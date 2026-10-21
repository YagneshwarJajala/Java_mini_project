package com.astraai.simulation;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.time.Duration;

/** Immutable engine view; the coordinator snapshot reflects live agent and task state. */
public record SimulationEngineSnapshot(
        SimulationState state,
        long sequence,
        int logicalAgentCount,
        int executionWorkerCount,
        long candidateSpaceSize,
        long completedCandidateEvaluations,
        Optional<Duration> elapsedTime,
        OptionalDouble evaluationsPerSecond,
        Optional<CoordinatorSnapshot> coordination,
        Optional<ExecutionReport> executionReport,
        Optional<CleanupReport> cleanupReport,
        Optional<String> diagnostic,
        boolean operationPending) {
    public SimulationEngineSnapshot {
        Objects.requireNonNull(state, "state");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence cannot be negative");
        }
        if (logicalAgentCount < 0 || executionWorkerCount < 0 || candidateSpaceSize < 0
                || completedCandidateEvaluations < 0 || completedCandidateEvaluations > candidateSpaceSize) {
            throw new IllegalArgumentException("snapshot counts are inconsistent");
        }
        Objects.requireNonNull(elapsedTime, "elapsedTime");
        Objects.requireNonNull(evaluationsPerSecond, "evaluationsPerSecond");
        Objects.requireNonNull(coordination, "coordination");
        Objects.requireNonNull(executionReport, "executionReport");
        Objects.requireNonNull(cleanupReport, "cleanupReport");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (coordination.isPresent()) {
            long agentTotal = coordination.orElseThrow().agents().stream()
                    .mapToLong(AgentSnapshot::completedEvaluations).sum();
            if (completedCandidateEvaluations != agentTotal) {
                throw new IllegalArgumentException("snapshot total must match logical-agent evaluation totals");
            }
        }
        if (executionReport.isPresent()) {
            ExecutionReport report = executionReport.orElseThrow();
            if (completedCandidateEvaluations != report.completedEvaluations()
                    || report.result().evaluationEvidence().orElseThrow().candidateCount() != candidateSpaceSize) {
                throw new IllegalArgumentException("snapshot totals and candidate space must match the execution report");
            }
        }
    }

    /** A result is exposed only after the independent validator has accepted it. */
    public Optional<SimulationResult> validatedResult() {
        return executionReport.filter(report -> report.validation().valid()).map(ExecutionReport::result);
    }

    public Optional<ValidationReport> validation() {
        return executionReport.map(ExecutionReport::validation);
    }
}
