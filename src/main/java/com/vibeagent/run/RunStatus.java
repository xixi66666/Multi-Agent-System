package com.vibeagent.run;

public enum RunStatus {
    CREATED,
    PLANNING,
    IMPLEMENTING,
    TESTING,
    REVIEWING,
    WAITING_FOR_INPUT,
    WAITING_FOR_APPROVAL,
    PAUSED,
    NEEDS_ATTENTION,
    COMPLETED,
    COMPLETED_WITH_WARNINGS,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == COMPLETED_WITH_WARNINGS
                || this == FAILED
                || this == CANCELLED;
    }
}
