package com.vibeagent.approval;

import java.time.Instant;
import java.util.UUID;

public record Approval(
        UUID id,
        UUID runId,
        UUID taskId,
        String actionType,
        String requestPayload,
        ApprovalStatus status,
        String decisionNote,
        Instant requestedAt,
        Instant decidedAt) {
}
