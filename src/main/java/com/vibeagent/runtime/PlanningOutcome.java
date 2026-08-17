package com.vibeagent.runtime;

public record PlanningOutcome(
        ExecutionPlan plan,
        AgentTaskExecution execution) {
}
