package com.astraai.simulation;

import java.util.List;
import java.util.Objects;

/** Immutable, consistent inspection view of logical agents and their tasks. */
public record CoordinatorSnapshot(List<AgentSnapshot> agents, List<TaskSnapshot> tasks) {
    public CoordinatorSnapshot {
        agents = List.copyOf(Objects.requireNonNull(agents, "agents"));
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
    }
}
