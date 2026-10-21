package com.astraai.simulation;

/** Lifecycle states; PAUSING and STOPPING are transitional states. */
public enum SimulationState {
    READY,
    RUNNING,
    PAUSING,
    PAUSED,
    STOPPING,
    COMPLETED,
    EXHAUSTED,
    STOPPED,
    FAILED
}
