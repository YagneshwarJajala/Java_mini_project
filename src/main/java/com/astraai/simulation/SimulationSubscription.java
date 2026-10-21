package com.astraai.simulation;

import java.time.Duration;

/** A bounded asynchronous engine subscription. Closing is non-blocking. */
public interface SimulationSubscription extends AutoCloseable {
    /** Snapshot captured atomically with listener registration. */
    SimulationEngineSnapshot initialSnapshot();

    boolean isClosed();

    /** Whether the delivery thread has actually terminated. */
    boolean isTerminated();

    /** Optional bounded wait for delivery-thread termination; never called by the engine. */
    boolean awaitTermination(Duration timeout) throws InterruptedException;

    @Override
    void close();
}
