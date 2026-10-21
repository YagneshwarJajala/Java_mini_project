package com.astraai.ui;

import com.astraai.simulation.SimulationState;
import java.util.Objects;

/** Maps every engine lifecycle phase onto the application's three primary UI states. */
public final class PresentationStateMapper {
    private PresentationStateMapper() { }

    public static PresentationState map(SimulationState engineState, boolean inputSubmitted) {
        Objects.requireNonNull(engineState, "engineState");
        if (!inputSubmitted) {
            return PresentationState.PASSWORD_ENTRY;
        }
        return switch (engineState) {
            case COMPLETED, EXHAUSTED, STOPPED, FAILED -> PresentationState.FINAL_RESULT;
            case READY, RUNNING, PAUSING, PAUSED, STOPPING -> PresentationState.PROCESSING;
        };
    }
}
