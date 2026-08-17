package com.vibeagent.agent;

import java.time.Instant;

public record AgentResult(
        AgentRole role,
        boolean successful,
        String summary,
        Instant completedAt) {

    public static AgentResult success(AgentRole role, String summary) {
        return new AgentResult(role, true, summary, Instant.now());
    }
}
