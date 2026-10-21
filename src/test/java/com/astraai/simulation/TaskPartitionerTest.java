package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskPartitionerTest {
    @Test
    void defaultConfigurationCreatesOneHundredAdjacentRangesCoveringAllCandidates() {
        List<SimulationTask> tasks = TaskPartitioner.partition(SimulationConfig.defaults());

        assertEquals(100, tasks.size());
        assertEquals(0, tasks.getFirst().candidateRange().startInclusive());
        assertEquals(100_000, tasks.getLast().candidateRange().endExclusive());
        for (int i = 0; i < tasks.size(); i++) {
            assertEquals(TaskId.of(i + 1), tasks.get(i).id());
            assertEquals(1_000, tasks.get(i).candidateRange().size());
            if (i > 0) {
                assertEquals(tasks.get(i - 1).candidateRange().endExclusive(),
                        tasks.get(i).candidateRange().startInclusive());
            }
        }
    }

    @Test
    void eachDefaultCandidateBelongsToExactlyOneTask() {
        List<SimulationTask> tasks = TaskPartitioner.partition(SimulationConfig.defaults());

        for (int index = 0; index < 100_000; index++) {
            int containing = 0;
            for (SimulationTask task : tasks) {
                if (task.candidateRange().contains(index)) {
                    containing++;
                }
            }
            assertEquals(1, containing, "candidate index " + index);
        }
        assertEquals(TaskId.of(5), taskContaining(tasks, 4_271).id());
    }

    @Test
    void smallerTestConfigurationPartitionsItsSpaceCorrectly() {
        SimulationConfig config = config(12, 3);
        List<SimulationTask> tasks = TaskPartitioner.partition(config);

        assertEquals(3, tasks.size());
        assertEquals(new CandidateRange(0, 4), tasks.get(0).candidateRange());
        assertEquals(new CandidateRange(4, 8), tasks.get(1).candidateRange());
        assertEquals(new CandidateRange(8, 12), tasks.get(2).candidateRange());
    }

    @Test
    void rejectsInvalidRangeAndNullConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new CandidateRange(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CandidateRange(2, 2));
        assertThrows(NullPointerException.class, () -> TaskPartitioner.partition(null));
        assertThrows(IllegalArgumentException.class, () -> config(10, 3));
        assertThrows(IllegalArgumentException.class, () -> config(Integer.MAX_VALUE, 100));
    }

    @Test
    void rangesAndPartitionListAreImmutableValues() {
        List<SimulationTask> tasks = TaskPartitioner.partition(config(12, 3));

        assertThrows(UnsupportedOperationException.class, () -> tasks.add(tasks.getFirst()));
        assertTrue(tasks.getFirst().candidateRange().contains(0));
        assertFalse(tasks.getFirst().candidateRange().contains(4));
    }

    private static SimulationTask taskContaining(List<SimulationTask> tasks, int index) {
        return tasks.stream().filter(task -> task.candidateRange().contains(index)).findFirst().orElseThrow();
    }

    private static SimulationConfig config(int candidates, int taskCount) {
        return SimulationConfig.forTesting(2, candidates, taskCount, 0, 5, Duration.ofMillis(200));
    }
}
