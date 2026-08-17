package com.vibeagent.runtime;

public enum AgentTaskStatus {
    PENDING,
    RUNNING,
    WAITING_FOR_INPUT,
    WAITING_FOR_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
