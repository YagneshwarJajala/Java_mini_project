package com.astraai.ui;

import com.astraai.simulation.AgentId;
import com.astraai.simulation.AgentSnapshot;
import com.astraai.simulation.CleanupReport;
import com.astraai.simulation.EvaluationEvidence;
import com.astraai.simulation.ExecutionReport;
import com.astraai.simulation.ResultOutcome;
import com.astraai.simulation.SimulationEngineSnapshot;
import com.astraai.simulation.SimulationResult;
import com.astraai.simulation.SimulationState;
import com.astraai.simulation.TaskId;
import com.astraai.simulation.TaskSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/** Execution-derived values suitable for the final Results page. */
public final class SimulationResultSummary {
    private final SimulationState terminalState;
    private final Optional<ResultOutcome> outcome;
    private final Optional<Boolean> targetDiscovered;
    private final Optional<String> discoveredCandidate;
    private final OptionalLong completedEvaluations;
    private final long searchSpaceSize;
    private final Optional<Duration> elapsedTime;
    private final OptionalDouble evaluationsPerSecond;
    private final OptionalDouble progress;
    private final int logicalAgentCount;
    private final List<AgentWork> agentWork;
    private final Optional<CleanupReport> cleanupReport;
    private final Optional<String> diagnostic;

    private SimulationResultSummary(SimulationState terminalState, Optional<ResultOutcome> outcome,
            Optional<Boolean> targetDiscovered,
            Optional<String> discoveredCandidate, OptionalLong completedEvaluations, long searchSpaceSize,
            Optional<Duration> elapsedTime, OptionalDouble evaluationsPerSecond, OptionalDouble progress,
            int logicalAgentCount, List<AgentWork> agentWork, Optional<CleanupReport> cleanupReport,
            Optional<String> diagnostic) {
        this.terminalState = Objects.requireNonNull(terminalState, "terminalState");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.targetDiscovered = Objects.requireNonNull(targetDiscovered, "targetDiscovered");
        this.discoveredCandidate = Objects.requireNonNull(discoveredCandidate, "discoveredCandidate");
        this.completedEvaluations = Objects.requireNonNull(completedEvaluations, "completedEvaluations");
        this.elapsedTime = Objects.requireNonNull(elapsedTime, "elapsedTime");
        this.evaluationsPerSecond = Objects.requireNonNull(evaluationsPerSecond, "evaluationsPerSecond");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.agentWork = List.copyOf(Objects.requireNonNull(agentWork, "agentWork"));
        this.cleanupReport = Objects.requireNonNull(cleanupReport, "cleanupReport");
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        if (searchSpaceSize < 0 || logicalAgentCount < 0) {
            throw new IllegalArgumentException("search space and logical-agent counts cannot be negative");
        }
        this.searchSpaceSize = searchSpaceSize;
        this.logicalAgentCount = logicalAgentCount;
    }

    public SimulationState terminalState() { return terminalState; }
    public Optional<ResultOutcome> outcome() { return outcome; }
    public Optional<Boolean> targetDiscovered() { return targetDiscovered; }
    public Optional<String> discoveredCandidate() { return discoveredCandidate; }
    public OptionalLong completedEvaluations() { return completedEvaluations; }
    public long searchSpaceSize() { return searchSpaceSize; }
    public Optional<Duration> elapsedTime() { return elapsedTime; }
    public OptionalDouble evaluationsPerSecond() { return evaluationsPerSecond; }
    public OptionalDouble progress() { return progress; }
    public int logicalAgentCount() { return logicalAgentCount; }
    public List<AgentWork> agentWork() { return agentWork; }
    public Optional<CleanupReport> cleanupReport() { return cleanupReport; }
    public Optional<String> diagnostic() { return diagnostic; }

    public static SimulationResultSummary from(SimulationEngineSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Optional<ExecutionReport> report = snapshot.executionReport()
                .filter(value -> value.validation().valid())
                .filter(value -> value.result().evaluationEvidence().isPresent())
                .filter(value -> value.result().evaluationEvidence().orElseThrow().candidateCount()
                        == snapshot.candidateSpaceSize());
        Optional<EvaluationEvidence> evidence = report.flatMap(value -> value.result().evaluationEvidence());
        Optional<SimulationResult> result = report.map(ExecutionReport::result);

        Optional<ResultOutcome> outcome = result.map(SimulationResult::outcome);
        Optional<Boolean> discovered = result.flatMap(value -> switch (value.outcome()) {
            case MATCH -> value.matchedIndex().isPresent() && evidence
                    .map(item -> item.completedEvaluationsAt(value.matchedIndex().getAsInt()) == 1).orElse(false)
                            ? Optional.of(true) : Optional.empty();
            case EXHAUSTED -> Optional.of(false);
            case STOPPED, FAILED -> Optional.empty();
        });
        Optional<String> candidate = result.filter(value -> value.outcome() == ResultOutcome.MATCH)
                .filter(value -> discovered.orElse(false))
                .flatMap(SimulationResult::candidateRepresentation);
        OptionalLong completed = evidence.isPresent()
                ? OptionalLong.of(evidence.orElseThrow().totalCompletedEvaluations()) : OptionalLong.empty();
        long searchSpace = snapshot.candidateSpaceSize();
        OptionalDouble progress = completed.isPresent() && searchSpace > 0
                ? OptionalDouble.of((double) completed.getAsLong() / searchSpace) : OptionalDouble.empty();
        OptionalDouble throughput = OptionalDouble.empty();
        if (completed.isPresent() && snapshot.elapsedTime().isPresent()) {
            long elapsedNanos = snapshot.elapsedTime().orElseThrow().toNanos();
            if (elapsedNanos > 0) {
                throughput = OptionalDouble.of(completed.getAsLong() / (elapsedNanos / 1_000_000_000.0));
            }
        }

        List<AgentWork> work = report.map(SimulationResultSummary::agentWork).orElseGet(List::of);
        return new SimulationResultSummary(snapshot.state(), outcome, discovered, candidate, completed, searchSpace,
                snapshot.elapsedTime(), throughput, progress, snapshot.logicalAgentCount(), work,
                snapshot.cleanupReport(), snapshot.diagnostic());
    }

    private static List<AgentWork> agentWork(ExecutionReport report) {
        Map<AgentId, List<TaskId>> assignments = new HashMap<>();
        for (TaskSnapshot task : report.tasks()) {
            task.owner().ifPresent(owner -> assignments.computeIfAbsent(owner, ignored -> new ArrayList<>())
                    .add(task.id()));
        }
        ArrayList<AgentWork> work = new ArrayList<>();
        for (AgentSnapshot agent : report.agents()) {
            List<TaskId> tasks = assignments.getOrDefault(agent.id(), List.of());
            work.add(new AgentWork(agent.id(), tasks, agent.completedEvaluations()));
        }
        return List.copyOf(work);
    }

    public record AgentWork(AgentId agent, List<TaskId> assignedTasks, long completedEvaluations) {
        public AgentWork {
            Objects.requireNonNull(agent, "agent");
            assignedTasks = List.copyOf(Objects.requireNonNull(assignedTasks, "assignedTasks"));
            if (completedEvaluations < 0) {
                throw new IllegalArgumentException("completedEvaluations cannot be negative");
            }
        }
    }
}
