package com.astraai.simulation;

import java.util.Objects;

/** Immutable link between a logical agent and its uniquely owned task. */
public record TaskAssignment(AgentId agentId, SimulationTask task) {
    public TaskAssignment {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(task, "task");
    }
}
