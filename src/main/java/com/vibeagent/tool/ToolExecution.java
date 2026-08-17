package com.vibeagent.tool;

import java.time.Instant;
import java.util.UUID;

public record ToolExecution(
        UUID id,
        UUID runId,
        UUID taskId,
        String toolName,
        String status,
        Integer exitCode,
        long durationMillis,
        Instant createdAt) {
}
