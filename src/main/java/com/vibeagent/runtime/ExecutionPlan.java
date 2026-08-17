package com.vibeagent.runtime;

import java.util.List;

public record ExecutionPlan(
        String summary,
        boolean requiresArchitecture,
        boolean requiresResearch,
        List<PlannedTask> implementationTasks,
        List<String> acceptanceCriteria) {

    public ExecutionPlan {
        implementationTasks = implementationTasks == null ? List.of() : List.copyOf(implementationTasks);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }
}
