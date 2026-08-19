package com.vibeagent.model;

import com.vibeagent.agent.AgentRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ModelUsage(
        UUID id,
        UUID runId,
        UUID taskId,
        AgentRole role,
        String provider,
        String model,
        String finishReason,
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long cachedInputTokens,
        long totalTokens,
        BigDecimal estimatedCost,
        boolean estimated,
        long latencyMillis,
        String requestStatus,
        String failureType,
        Instant createdAt) {
}
