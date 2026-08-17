package com.vibeagent.event;

import java.time.Instant;
import java.util.UUID;

public record RunEvent(
        long id,
        UUID runId,
        String type,
        String payload,
        Instant createdAt) {
}
