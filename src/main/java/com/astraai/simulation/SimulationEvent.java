package com.astraai.simulation;

import java.util.Objects;

/**
 * Immutable observation. The sequence is the engine-wide revision: every
 * published update increments it, while initial snapshots reuse the current
 * revision. Within one subscription, delivered revisions increase after its
 * initial event; gaps indicate coalescing or recovery and should trigger a
 * fresh {@link SimulationEngine#snapshot()} read.
 */
public record SimulationEvent(long sequence, EngineEventType type,
        SimulationEngineSnapshot snapshot, boolean resyncRequired) {
    public SimulationEvent {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence cannot be negative");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
