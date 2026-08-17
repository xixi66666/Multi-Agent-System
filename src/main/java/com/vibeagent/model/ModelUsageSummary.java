package com.vibeagent.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ModelUsageSummary(
        UUID runId,
        long calls,
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long cachedInputTokens,
        long totalTokens,
        BigDecimal estimatedCost,
        List<ModelUsage> usages) {
}
