package com.astraai.simulation;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Immediate lifecycle-command response plus an asynchronous operation-completion stage. */
public record EngineCommandResult(CommandDisposition disposition, SimulationState state,
        String message, CompletionStage<SimulationEngineSnapshot> completion) {
    public EngineCommandResult {
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(completion, "completion");
    }
}
