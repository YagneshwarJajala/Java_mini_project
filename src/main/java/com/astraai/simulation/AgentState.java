package com.astraai.simulation;

/** Publicly observable states for logical agents, not execution threads. */
public enum AgentState {
    IDLE,
    WAITING,
    WORKING,
    PAUSED,
    COMPLETED,
    ERROR,
    CANCELLED
}
