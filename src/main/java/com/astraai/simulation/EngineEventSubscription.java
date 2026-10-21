package com.astraai.simulation;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

/** One bounded mailbox and one isolated daemon delivery thread per subscriber. */
final class EngineEventSubscription implements SimulationSubscription {
    static final int MAILBOX_CAPACITY = 16;

    private static final System.Logger LOGGER = System.getLogger(EngineEventSubscription.class.getName());

    private final Object monitor = new Object();
    private final ArrayDeque<SimulationEvent> mailbox = new ArrayDeque<>(MAILBOX_CAPACITY);
    private final Consumer<SimulationEvent> listener;
    private final Consumer<EngineEventSubscription> onTermination;
    private final int number;
    private final SimulationEngineSnapshot initialSnapshot;
    private final Thread deliveryThread;
    private boolean closed;
    private boolean terminated;
    private boolean droppedProgress;

    EngineEventSubscription(Consumer<SimulationEvent> listener,
            SimulationEngineSnapshot initialSnapshot, SimulationEvent initialEvent, int number,
            Consumer<EngineEventSubscription> onTermination) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.onTermination = Objects.requireNonNull(onTermination, "onTermination");
        this.number = number;
        this.initialSnapshot = Objects.requireNonNull(initialSnapshot, "initialSnapshot");
        mailbox.add(Objects.requireNonNull(initialEvent, "initialEvent"));
        deliveryThread = new Thread(this::deliver, "astra-engine-subscriber-" + number);
        deliveryThread.setDaemon(true);
    }

    void start() {
        deliveryThread.start();
    }

    int number() {
        return number;
    }

    int mailboxSize() {
        synchronized (monitor) {
            return mailbox.size();
        }
    }

    void offer(SimulationEvent offered) {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            SimulationEvent event = withPendingGap(offered);
            if (event.type() == EngineEventType.PROGRESS) {
                SimulationEvent tail = mailbox.peekLast();
                if (tail != null && tail.type() == EngineEventType.PROGRESS) {
                    mailbox.removeLast();
                    mailbox.addLast(new SimulationEvent(event.sequence(), event.type(), event.snapshot(), true));
                    monitor.notifyAll();
                    return;
                }
                if (mailbox.size() >= MAILBOX_CAPACITY) {
                    droppedProgress = true;
                    return;
                }
            } else if (mailbox.size() >= MAILBOX_CAPACITY) {
                boolean removedProgress = mailbox.removeIf(queued -> queued.type() == EngineEventType.PROGRESS);
                if (removedProgress && !event.resyncRequired()) {
                    event = new SimulationEvent(event.sequence(), event.type(), event.snapshot(), true);
                }
                if (mailbox.size() >= MAILBOX_CAPACITY) {
                    mailbox.clear();
                    event = new SimulationEvent(offered.sequence(), EngineEventType.RESYNC_REQUIRED,
                            offered.snapshot(), true);
                }
            }
            mailbox.addLast(event);
            monitor.notifyAll();
        }
    }

    private SimulationEvent withPendingGap(SimulationEvent event) {
        if (!droppedProgress) {
            return event;
        }
        droppedProgress = false;
        return new SimulationEvent(event.sequence(), event.type(), event.snapshot(), true);
    }

    private void deliver() {
        try {
            while (true) {
                SimulationEvent next;
                synchronized (monitor) {
                    while (!closed && mailbox.isEmpty()) {
                        monitor.wait();
                    }
                    if (closed) {
                        return;
                    }
                    next = mailbox.removeFirst();
                }
                try {
                    listener.accept(next);
                } catch (Throwable failure) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Simulation event listener failed; subscription remains active", failure);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (monitor) {
                terminated = true;
                mailbox.clear();
                monitor.notifyAll();
            }
            onTermination.accept(this);
        }
    }

    @Override
    public SimulationEngineSnapshot initialSnapshot() {
        return initialSnapshot;
    }

    @Override
    public boolean isClosed() {
        synchronized (monitor) {
            return closed;
        }
    }

    @Override
    public boolean isTerminated() {
        synchronized (monitor) {
            return terminated;
        }
    }

    @Override
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout cannot be negative");
        }
        if (timeout.isZero()) {
            return isTerminated();
        }
        long millis = timeout.toMillis();
        int nanos = timeout.minusMillis(millis).getNano();
        deliveryThread.join(millis, nanos);
        return isTerminated();
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            mailbox.clear();
            monitor.notifyAll();
        }
        deliveryThread.interrupt();
    }
}
