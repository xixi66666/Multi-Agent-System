package com.vibeagent.runtime;

import com.vibeagent.model.ModelResponse;

public record AgentTaskExecution(
        AgentTask task,
        ModelResponse modelResponse) {
}
