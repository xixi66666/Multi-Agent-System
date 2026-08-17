package com.vibeagent.runtime;

import com.vibeagent.agent.AgentRole;

import java.time.Instant;
import java.util.UUID;

public record AgentTask(
        UUID id,
        UUID runId,
        UUID parentTaskId,
        AgentRole role,
        String specialty,
        String title,
        String instructions,
        AgentTaskStatus status,
        int attempt,
        int maxAttempts,
        String leaseOwner,
        Instant leaseUntil,
        String resultSummary,
        String failure,
        Instant createdAt,
        Instant updatedAt) {
}
