package com.vibeagent.run;

import com.vibeagent.agent.AgentResult;
import com.vibeagent.agent.AgentRole;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RunSnapshot(
        UUID id,
        UUID projectId,
        String requirement,
        String workspace,
        RunStatus status,
        Map<AgentRole, AgentResult> results,
        String summary,
        String failure,
        Instant createdAt,
        Instant updatedAt) {
}
