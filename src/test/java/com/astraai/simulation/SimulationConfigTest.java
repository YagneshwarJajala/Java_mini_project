package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SimulationConfigTest {
    @Test
    void defaultsMatchTheApprovedDemonstrationConfiguration() {
        SimulationConfig config = SimulationConfig.defaults();

        assertEquals(100, config.logicalAgentCount());
        assertEquals(8, config.workerCount());
        assertEquals(100_000, config.candidateCount());
        assertEquals(100, config.taskCount());
        assertEquals(4_271, config.expectedIndex());
        assertEquals(1_000, config.candidatesPerTask());
        assertEquals(100, config.candidateBatchSize());
        assertEquals(Duration.ofSeconds(3), config.cleanupTimeout());
        assertEquals(500, SimulationConfig.MAX_ACTIVITY_ENTRIES);
    }

    @Test
    void logicalAgentCountIsIndependentOfWorkerCount() {
        SimulationConfig config = SimulationConfig.forTesting(2, 20, 4, 7, 5,
                Duration.ofMillis(200));

        assertEquals(100, config.logicalAgentCount());
        assertEquals(2, config.workerCount());
    }

    @Test
    void acceptsSmallerEvenlyPartitionedTestConfiguration() {
        SimulationConfig config = SimulationConfig.forTesting(1, 20, 4, 19, 1,
                Duration.ofMillis(1));

        assertEquals(5, config.candidatesPerTask());
    }

    @Test
    void acceptsSingleCandidateSearchSpace() {
        SimulationConfig config = SimulationConfig.forTesting(1, 1, 1, 0, 1,
                Duration.ofMillis(200));

        assertEquals(1, config.candidateCount());
        assertEquals(1, config.candidatesPerTask());
        assertEquals(0, config.expectedIndex());
    }

    @Test
    void rejectsInvalidConfigurationBeforeItCanBeUsedToStartWorkers() {
        assertThrows(IllegalArgumentException.class, () -> config(0, 20, 4, 1, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> config(9, 20, 4, 1, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> config(1, 0, 1, 0, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> config(1, 20, 0, 1, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> config(1, 20, 21, 1, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> config(1, 20, 3, 1, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> config(1, 20, 4, 20, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> config(1, 20, 4, 1, 0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> config(1, 20, 4, 1, 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> config(1, 20, 4, 1, 1, Duration.ofSeconds(31)));
        assertThrows(NullPointerException.class, () -> config(1, 20, 4, 1, 1, null));
    }

    private static SimulationConfig config(int workers, int candidates, int tasks, int expected,
            int batch, Duration timeout) {
        return SimulationConfig.forTesting(workers, candidates, tasks, expected, batch, timeout);
    }
}
