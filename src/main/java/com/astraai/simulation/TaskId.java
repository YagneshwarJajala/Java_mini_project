package com.astraai.simulation;

/** Stable one-based identity for a task in a run. */
public record TaskId(int number) implements Comparable<TaskId> {
    public TaskId {
        if (number < 1 || number > SimulationConfig.DEFAULT_TASK_COUNT) {
            throw new IllegalArgumentException("task number must be between 1 and "
                    + SimulationConfig.DEFAULT_TASK_COUNT);
        }
    }

    public static TaskId of(int number) {
        return new TaskId(number);
    }

    public String displayName() {
        return "task-%03d".formatted(number);
    }

    @Override
    public int compareTo(TaskId other) {
        return Integer.compare(number, other.number);
    }
}
