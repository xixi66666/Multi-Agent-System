package com.vibeagent.runtime;

public record ReviewExecution(
        ReviewOutcome outcome,
        AgentTaskExecution execution) {
}
