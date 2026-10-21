package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TaskManagerTest {
    @Test
    void assignsTasksInTaskIdOrderAndOnlyOneActiveTaskPerAgent() {
        TaskManager manager = new TaskManager(config(4, 2));
        AgentId agent = AgentId.of(1);

        TaskSnapshot first = manager.claimNext(agent).orElseThrow();
        assertEquals(TaskId.of(1), first.id());
        assertTrue(manager.claimNext(agent).isEmpty());
        assertEquals(TaskId.of(2), manager.claimNext(AgentId.of(2)).orElseThrow().id());
    }

    @Test
    void specificTaskCannotBeClaimedByAnotherAgentOrReassigned() {
        TaskManager manager = new TaskManager(config(4, 2));
        TaskId taskId = TaskId.of(1);

        TaskSnapshot claimed = manager.claim(taskId, AgentId.of(1)).orElseThrow();
        assertEquals(Optional.of(AgentId.of(1)), claimed.owner());
        assertTrue(manager.claim(taskId, AgentId.of(2)).isEmpty());
        assertEquals(Optional.of(AgentId.of(1)), manager.snapshot(taskId).orElseThrow().owner());
    }

    @Test
    void onlyOwnerCanCompleteAndCompletedTaskCannotBeClaimedAgain() {
        TaskManager manager = new TaskManager(config(4, 2));
        AgentId owner = AgentId.of(1);
        TaskId taskId = TaskId.of(1);
        manager.claim(taskId, owner).orElseThrow();

        assertFalse(manager.complete(taskId, AgentId.of(2)));
        assertTrue(manager.start(taskId, owner));
        assertTrue(manager.complete(taskId, owner));
        assertEquals(TaskState.COMPLETED, manager.snapshot(taskId).orElseThrow().state());
        assertTrue(manager.claim(taskId, AgentId.of(2)).isEmpty());
        assertTrue(manager.claimNext(owner).isPresent());
    }

    @Test
    void cancelledTasksCannotBeClaimedAgainAndReleaseTheOwner() {
        TaskManager manager = new TaskManager(config(4, 2));
        AgentId owner = AgentId.of(1);
        TaskId taskId = TaskId.of(1);
        manager.claim(taskId, owner).orElseThrow();

        assertTrue(manager.cancel(taskId));
        TaskSnapshot cancelled = manager.snapshot(taskId).orElseThrow();
        assertEquals(TaskState.CANCELLED, cancelled.state());
        assertEquals(Optional.of(owner), cancelled.owner());
        assertTrue(manager.claim(taskId, AgentId.of(2)).isEmpty());
        assertTrue(manager.claimNext(owner).isPresent());
        assertFalse(manager.cancel(taskId));
    }

    @Test
    void concurrentClaimsForOneTaskHaveExactlyOneWinner() throws Exception {
        TaskManager manager = new TaskManager(config(4, 2));
        TaskId taskId = TaskId.of(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<TaskSnapshot>> first = callers.submit(() -> claimTogether(manager, taskId,
                    AgentId.of(1), ready, start));
            Future<Optional<TaskSnapshot>> second = callers.submit(() -> claimTogether(manager, taskId,
                    AgentId.of(2), ready, start));

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            Optional<TaskSnapshot> firstResult = first.get(2, TimeUnit.SECONDS);
            Optional<TaskSnapshot> secondResult = second.get(2, TimeUnit.SECONDS);

            assertEquals(1, (firstResult.isPresent() ? 1 : 0) + (secondResult.isPresent() ? 1 : 0));
            assertEquals(TaskState.ASSIGNED, manager.snapshot(taskId).orElseThrow().state());
        } finally {
            start.countDown();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void snapshotsAndSnapshotListsAreImmutableAndDetached() {
        TaskManager manager = new TaskManager(config(4, 2));
        List<TaskSnapshot> before = manager.snapshots();
        manager.claimNext(AgentId.of(1)).orElseThrow();

        assertEquals(TaskState.QUEUED, before.getFirst().state());
        assertThrows(UnsupportedOperationException.class, before::clear);
        assertEquals(TaskState.ASSIGNED, manager.snapshots().getFirst().state());
    }

    private static Optional<TaskSnapshot> claimTogether(TaskManager manager, TaskId taskId, AgentId agentId,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out waiting for concurrent claim start");
        }
        return manager.claim(taskId, agentId);
    }

    private static SimulationConfig config(int candidates, int taskCount) {
        return SimulationConfig.forTesting(2, candidates, taskCount, 0, 2, Duration.ofMillis(200));
    }
}
