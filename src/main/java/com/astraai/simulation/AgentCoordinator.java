package com.astraai.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Coordinates the fixed set of logical agents with TaskManager; owns no threads. */
public final class AgentCoordinator {
    private final TaskManager taskManager;
    private final List<AgentEntry> agents;
    private int nextAgentIndex;

    public AgentCoordinator(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        taskManager = new TaskManager(config);
        ArrayList<AgentEntry> logicalAgents = new ArrayList<>(SimulationConfig.LOGICAL_AGENT_COUNT);
        for (int number = 1; number <= SimulationConfig.LOGICAL_AGENT_COUNT; number++) {
            logicalAgents.add(new AgentEntry(AgentId.of(number)));
        }
        agents = List.copyOf(logicalAgents);
    }

    /** Assigns each task once in task-ID order to agents in stable ID order. */
    synchronized Optional<TaskAssignment> claimNextAssignment() {
        if (nextAgentIndex >= agents.size()) {
            return Optional.empty();
        }
        AgentEntry agent = agents.get(nextAgentIndex);
        Optional<TaskSnapshot> claimed = taskManager.claimNext(agent.id);
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        nextAgentIndex++;
        TaskSnapshot task = claimed.orElseThrow();
        agent.state = AgentState.WAITING;
        agent.currentTask = Optional.of(task.id());
        return Optional.of(new TaskAssignment(agent.id,
                new SimulationTask(task.id(), task.candidateRange())));
    }

    synchronized boolean begin(TaskAssignment assignment) {
        AgentEntry agent = requireAgent(assignment.agentId());
        if (agent.state != AgentState.WAITING || !agent.currentTask.equals(Optional.of(assignment.task().id()))) {
            return false;
        }
        if (!taskManager.start(assignment.task().id(), agent.id)) {
            return false;
        }
        agent.state = AgentState.WORKING;
        return true;
    }

    synchronized boolean pause(TaskAssignment assignment) {
        AgentEntry agent = requireAgent(assignment.agentId());
        if (agent.state != AgentState.WORKING || !agent.currentTask.equals(Optional.of(assignment.task().id()))) {
            return false;
        }
        if (!taskManager.pause(assignment.task().id(), agent.id)) {
            return false;
        }
        agent.state = AgentState.PAUSED;
        return true;
    }

    synchronized boolean resume(TaskAssignment assignment) {
        AgentEntry agent = requireAgent(assignment.agentId());
        if (agent.state != AgentState.PAUSED || !agent.currentTask.equals(Optional.of(assignment.task().id()))) {
            return false;
        }
        if (!taskManager.resume(assignment.task().id(), agent.id)) {
            return false;
        }
        agent.state = AgentState.WORKING;
        return true;
    }

    synchronized void recordCompletedEvaluations(TaskAssignment assignment, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        AgentEntry agent = requireAgent(assignment.agentId());
        if (agent.state != AgentState.WORKING || !agent.currentTask.equals(Optional.of(assignment.task().id()))) {
            throw new IllegalStateException("agent does not own the active task");
        }
        agent.completedEvaluations = Math.addExact(agent.completedEvaluations, count);
    }

    synchronized boolean complete(TaskAssignment assignment) {
        AgentEntry agent = requireAgent(assignment.agentId());
        if (agent.state != AgentState.WORKING || !agent.currentTask.equals(Optional.of(assignment.task().id()))) {
            return false;
        }
        if (!taskManager.complete(assignment.task().id(), agent.id)) {
            return false;
        }
        agent.state = AgentState.COMPLETED;
        agent.currentTask = Optional.empty();
        return true;
    }

    synchronized boolean cancel(TaskAssignment assignment) {
        AgentEntry agent = requireAgent(assignment.agentId());
        if (!agent.currentTask.equals(Optional.of(assignment.task().id()))) {
            return false;
        }
        if (!taskManager.cancel(assignment.task().id())) {
            return false;
        }
        agent.state = AgentState.CANCELLED;
        agent.currentTask = Optional.empty();
        return true;
    }

    synchronized boolean fail(TaskAssignment assignment) {
        AgentEntry agent = requireAgent(assignment.agentId());
        if (!agent.currentTask.equals(Optional.of(assignment.task().id()))) {
            return false;
        }
        if (!taskManager.fail(assignment.task().id(), agent.id)) {
            return false;
        }
        agent.state = AgentState.ERROR;
        agent.currentTask = Optional.empty();
        return true;
    }

    synchronized void cancelUnassignedTasks() {
        for (TaskSnapshot task : taskManager.snapshots()) {
            if (task.state() == TaskState.QUEUED) {
                taskManager.cancel(task.id());
            }
        }
    }

    public synchronized CoordinatorSnapshot snapshot() {
        ArrayList<AgentSnapshot> agentSnapshots = new ArrayList<>(agents.size());
        for (AgentEntry agent : agents) {
            agentSnapshots.add(new AgentSnapshot(agent.id, agent.state, agent.currentTask,
                    agent.completedEvaluations));
        }
        return new CoordinatorSnapshot(agentSnapshots, taskManager.snapshots());
    }

    private AgentEntry requireAgent(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId");
        return agents.get(agentId.number() - 1);
    }

    private static final class AgentEntry {
        private final AgentId id;
        private AgentState state = AgentState.IDLE;
        private Optional<TaskId> currentTask = Optional.empty();
        private long completedEvaluations;

        private AgentEntry(AgentId id) {
            this.id = id;
        }
    }
}
