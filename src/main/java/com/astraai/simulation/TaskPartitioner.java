package com.astraai.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Creates a deterministic, gap-free partition before task state is initialized. */
public final class TaskPartitioner {
    private TaskPartitioner() {
    }

    public static List<SimulationTask> partition(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        int candidateCount = config.candidateCount();
        int taskCount = config.taskCount();
        ArrayList<SimulationTask> tasks = new ArrayList<>(taskCount);
        long previousEnd = 0;

        for (int ordinal = 0; ordinal < taskCount; ordinal++) {
            long start = (long) ordinal * candidateCount / taskCount;
            long end = ((long) ordinal + 1L) * candidateCount / taskCount;
            if (start != previousEnd || end <= start || end > candidateCount) {
                throw new IllegalArgumentException("configuration does not form a valid candidate partition");
            }
            CandidateRange range = new CandidateRange(Math.toIntExact(start), Math.toIntExact(end));
            tasks.add(new SimulationTask(TaskId.of(ordinal + 1), range));
            previousEnd = end;
        }

        if (previousEnd != candidateCount) {
            throw new IllegalArgumentException("task ranges do not cover the configured candidate space");
        }
        return List.copyOf(tasks);
    }
}
