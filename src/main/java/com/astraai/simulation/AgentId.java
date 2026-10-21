package com.astraai.simulation;

/** Stable one-based identity for a logical simulated agent. */
public record AgentId(int number) implements Comparable<AgentId> {
    public AgentId {
        if (number < 1 || number > SimulationConfig.LOGICAL_AGENT_COUNT) {
            throw new IllegalArgumentException("agent number must be between 1 and "
                    + SimulationConfig.LOGICAL_AGENT_COUNT);
        }
    }

    public static AgentId of(int number) {
        return new AgentId(number);
    }

    public String displayName() {
        return "agent-%03d".formatted(number);
    }

    @Override
    public int compareTo(AgentId other) {
        return Integer.compare(number, other.number);
    }
}
