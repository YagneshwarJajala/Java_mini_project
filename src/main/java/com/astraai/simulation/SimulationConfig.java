package com.astraai.simulation;

import java.time.Duration;
import java.util.Objects;

/** Immutable, validated limits and defaults for one local simulation run. */
public record SimulationConfig(
        int workerCount,
        int candidateCount,
        int taskCount,
        int expectedIndex,
        int candidateBatchSize,
        Duration cleanupTimeout) {

    public static final int LOGICAL_AGENT_COUNT = 100;
    public static final int DEFAULT_WORKER_COUNT = 8;
    public static final int DEFAULT_CANDIDATE_COUNT = 100_000;
    public static final int DEFAULT_TASK_COUNT = 100;
    public static final int DEFAULT_EXPECTED_INDEX = 4_271;
    public static final int DEFAULT_CANDIDATE_BATCH_SIZE = 100;
    public static final int MAX_ACTIVITY_ENTRIES = 500;
    public static final Duration DEFAULT_CLEANUP_TIMEOUT = Duration.ofSeconds(3);

    private static final int MAX_WORKER_COUNT = 8;
    private static final int MAX_CANDIDATE_COUNT = 100_000;
    private static final int MAX_TASK_COUNT = 100;
    private static final int MAX_CANDIDATE_BATCH_SIZE = 1_000;
    private static final Duration MAX_CLEANUP_TIMEOUT = Duration.ofSeconds(30);

    public SimulationConfig {
        Objects.requireNonNull(cleanupTimeout, "cleanupTimeout");
        if (workerCount < 1 || workerCount > MAX_WORKER_COUNT) {
            throw new IllegalArgumentException("workerCount must be between 1 and " + MAX_WORKER_COUNT);
        }
        if (candidateCount < 1 || candidateCount > MAX_CANDIDATE_COUNT) {
            throw new IllegalArgumentException("candidateCount must be between 1 and " + MAX_CANDIDATE_COUNT);
        }
        if (taskCount < 1 || taskCount > MAX_TASK_COUNT || taskCount > candidateCount) {
            throw new IllegalArgumentException("taskCount must be between 1 and min("
                    + MAX_TASK_COUNT + ", candidateCount)");
        }
        if (candidateCount % taskCount != 0) {
            throw new IllegalArgumentException("candidateCount must be evenly divisible by taskCount");
        }
        if (expectedIndex < 0 || expectedIndex >= candidateCount) {
            throw new IllegalArgumentException("expectedIndex must be within the candidate range");
        }
        if (candidateBatchSize < 1 || candidateBatchSize > MAX_CANDIDATE_BATCH_SIZE) {
            throw new IllegalArgumentException("candidateBatchSize must be between 1 and "
                    + MAX_CANDIDATE_BATCH_SIZE);
        }
        if (cleanupTimeout.isZero() || cleanupTimeout.isNegative()
                || cleanupTimeout.compareTo(MAX_CLEANUP_TIMEOUT) > 0) {
            throw new IllegalArgumentException("cleanupTimeout must be positive and no greater than "
                    + MAX_CLEANUP_TIMEOUT.toSeconds() + " seconds");
        }
    }

    public static SimulationConfig defaults() {
        return new SimulationConfig(DEFAULT_WORKER_COUNT, DEFAULT_CANDIDATE_COUNT,
                DEFAULT_TASK_COUNT, DEFAULT_EXPECTED_INDEX, DEFAULT_CANDIDATE_BATCH_SIZE,
                DEFAULT_CLEANUP_TIMEOUT);
    }

    /** Creates a smaller bounded configuration for deterministic tests. */
    public static SimulationConfig forTesting(int workerCount, int candidateCount, int taskCount,
            int expectedIndex, int candidateBatchSize, Duration cleanupTimeout) {
        return new SimulationConfig(workerCount, candidateCount, taskCount, expectedIndex,
                candidateBatchSize, cleanupTimeout);
    }

    public int logicalAgentCount() {
        return LOGICAL_AGENT_COUNT;
    }

    public int candidatesPerTask() {
        return candidateCount / taskCount;
    }
}
