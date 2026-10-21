package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.astraai.simulation.SimulationState;
import org.junit.jupiter.api.Test;

class PresentationStateMapperTest {
    @Test
    void onlyThreePrimaryPresentationStatesExist() {
        assertEquals(3, PresentationState.values().length);
        assertEquals(PresentationState.PASSWORD_ENTRY,
                PresentationStateMapper.map(SimulationState.READY, false));
        assertEquals(PresentationState.PROCESSING,
                PresentationStateMapper.map(SimulationState.READY, true));
    }

    @Test
    void lifecycleConditionsStayInsideProcessingUntilTerminal() {
        for (SimulationState state : new SimulationState[] { SimulationState.RUNNING, SimulationState.PAUSING,
                SimulationState.PAUSED, SimulationState.STOPPING }) {
            assertEquals(PresentationState.PROCESSING, PresentationStateMapper.map(state, true));
        }
        for (SimulationState state : new SimulationState[] { SimulationState.COMPLETED, SimulationState.EXHAUSTED,
                SimulationState.STOPPED, SimulationState.FAILED }) {
            assertEquals(PresentationState.FINAL_RESULT, PresentationStateMapper.map(state, true));
        }
    }
}
