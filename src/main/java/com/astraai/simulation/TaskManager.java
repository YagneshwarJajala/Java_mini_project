package com.astraai.simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Thread-safe task claim and terminal-state bookkeeping; creates no worker threads. */
public final class TaskManager {
    private final List<SimulationTask> tasks;
    private final Map<TaskId, TaskEntry> entriesById;
    private final Map<AgentId, TaskId> activeTaskByAgent = new HashMap<>();

    public TaskManager(SimulationConfig config) {
        // Build and validate every immutable range before initializing mutable task state.
        this.tasks = TaskPartitioner.partition(Objects.requireNonNull(config, "config"));
        Map<TaskId, TaskEntry> entries = new HashMap<>();
        for (SimulationTask task : tasks) {
            entries.put(task.id(), new TaskEntry(task));
        }
        this.entriesById = Map.copyOf(entries);
    }

    /** Returns the lowest-numbered unassigned task, if this agent owns no active task. */
    public synchronized Optional<TaskSnapshot> claimNext(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId");
        if (activeTaskByAgent.containsKey(agentId)) {
            return Optional.empty();
        }
        for (SimulationTask task : tasks) {
            TaskEntry entry = entriesById.get(task.id());
            if (entry.state == TaskState.QUEUED) {
                assign(entry, agentId);
                return Optional.of(entry.snapshot());
            }
        }
        return Optional.empty();
    }

    /** Atomically claims a specific task; useful when task selection is made by a caller. */
    public synchronized Optional<TaskSnapshot> claim(TaskId taskId, AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId");
        TaskEntry entry = requireEntry(taskId);
        if (activeTaskByAgent.containsKey(agentId) || entry.state != TaskState.QUEUED) {
            return Optional.empty();
        }
        assign(entry, agentId);
        return Optional.of(entry.snapshot());
    }

    /** Moves an owned assignment to active execution. */
    public synchronized boolean start(TaskId taskId, AgentId agentId) {
        TaskEntry entry = requireEntry(taskId);
        if (entry.state != TaskState.ASSIGNED || !entry.owner.equals(Optional.of(agentId))) {
            return false;
        }
        entry.state = TaskState.RUNNING;
        return true;
    }

    public synchronized boolean pause(TaskId taskId, AgentId agentId) {
        TaskEntry entry = requireEntry(taskId);
        if (entry.state != TaskState.RUNNING || !entry.owner.equals(Optional.of(agentId))) {
            return false;
        }
        entry.state = TaskState.PAUSED;
        return true;
    }

    public synchronized boolean resume(TaskId taskId, AgentId agentId) {
        TaskEntry entry = requireEntry(taskId);
        if (entry.state != TaskState.PAUSED || !entry.owner.equals(Optional.of(agentId))) {
            return false;
        }
        entry.state = TaskState.RUNNING;
        return true;
    }

    /** Marks a running task complete only for its current owner. */
    public synchronized boolean complete(TaskId taskId, AgentId agentId) {
        TaskEntry entry = requireEntry(taskId);
        if (entry.state != TaskState.RUNNING || !entry.owner.equals(Optional.of(agentId))) {
            return false;
        }
        entry.state = TaskState.COMPLETED;
        activeTaskByAgent.remove(agentId);
        return true;
    }

    /** Marks an assigned or running task failed for its current owner. */
    public synchronized boolean fail(TaskId taskId, AgentId agentId) {
        TaskEntry entry = requireEntry(taskId);
        if ((entry.state != TaskState.ASSIGNED && entry.state != TaskState.RUNNING
                && entry.state != TaskState.PAUSED)
                || !entry.owner.equals(Optional.of(agentId))) {
            return false;
        }
        entry.state = TaskState.FAILED;
        activeTaskByAgent.remove(agentId);
        return true;
    }

    /** Cancels an unassigned or assigned task. Assigned ownership is retained for inspection. */
    public synchronized boolean cancel(TaskId taskId) {
        TaskEntry entry = requireEntry(taskId);
        if (entry.state != TaskState.QUEUED && entry.state != TaskState.ASSIGNED
                && entry.state != TaskState.RUNNING && entry.state != TaskState.PAUSED) {
            return false;
        }
        entry.state = TaskState.CANCELLED;
        entry.owner.ifPresent(activeTaskByAgent::remove);
        return true;
    }

    public synchronized Optional<TaskSnapshot> snapshot(TaskId taskId) {
        return Optional.of(requireEntry(taskId).snapshot());
    }

    /** Returns snapshots in deterministic task-ID order. */
    public synchronized List<TaskSnapshot> snapshots() {
        ArrayList<TaskSnapshot> snapshots = new ArrayList<>(tasks.size());
        for (SimulationTask task : tasks) {
            snapshots.add(entriesById.get(task.id()).snapshot());
        }
        return List.copyOf(snapshots);
    }

    private void assign(TaskEntry entry, AgentId agentId) {
        entry.owner = Optional.of(agentId);
        entry.state = TaskState.ASSIGNED;
        activeTaskByAgent.put(agentId, entry.definition.id());
    }

    private TaskEntry requireEntry(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        TaskEntry entry = entriesById.get(taskId);
        if (entry == null) {
            throw new IllegalArgumentException("unknown task ID: " + taskId);
        }
        return entry;
    }

    private static final class TaskEntry {
        private final SimulationTask definition;
        private TaskState state = TaskState.QUEUED;
        private Optional<AgentId> owner = Optional.empty();

        private TaskEntry(SimulationTask definition) {
            this.definition = definition;
        }

        private TaskSnapshot snapshot() {
            return new TaskSnapshot(definition.id(), definition.candidateRange(), state, owner);
        }
    }
}
