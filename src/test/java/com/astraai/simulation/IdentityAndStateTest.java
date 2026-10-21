package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IdentityAndStateTest {
    @Test
    void agentIdsHaveStableDisplayNamesAndBounds() {
        assertEquals("agent-001", AgentId.of(1).displayName());
        assertEquals("agent-100", AgentId.of(100).displayName());
        assertThrows(IllegalArgumentException.class, () -> AgentId.of(0));
        assertThrows(IllegalArgumentException.class, () -> AgentId.of(101));
    }

    @Test
    void taskIdsHaveStableDisplayNamesAndBounds() {
        assertEquals("task-001", TaskId.of(1).displayName());
        assertEquals("task-100", TaskId.of(100).displayName());
        assertThrows(IllegalArgumentException.class, () -> TaskId.of(0));
        assertThrows(IllegalArgumentException.class, () -> TaskId.of(101));
    }

    @Test
    void lifecycleAndAgentStatesIncludeRequiredValues() {
        assertEquals(SimulationState.PAUSING, SimulationState.valueOf("PAUSING"));
        assertEquals(SimulationState.STOPPING, SimulationState.valueOf("STOPPING"));
        assertEquals(7, AgentState.values().length);
        assertEquals(7, TaskState.values().length);
    }
}
