package com.vibeagent.model;

import java.math.BigDecimal;

public record ModelResponse(
        String content,
        String provider,
        String model,
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long cachedInputTokens,
        long totalTokens,
        BigDecimal estimatedCost,
        boolean usageEstimated,
        long latencyMillis) {
}
