package com.vibeagent.agent;

import java.util.Map;
import java.util.UUID;

public record AgentContext(
        UUID runId,
        String requirement,
        String workspace,
        Map<AgentRole, AgentResult> priorResults) {

    public AgentContext {
        priorResults = Map.copyOf(priorResults);
    }
}
