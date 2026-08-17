package com.vibeagent.collaboration;

import com.vibeagent.agent.AgentRole;

import java.time.Instant;
import java.util.UUID;

public record AgentMessage(
        UUID id,
        UUID runId,
        UUID taskId,
        AgentRole senderRole,
        AgentRole recipientRole,
        String messageType,
        String schemaVersion,
        String content,
        Instant createdAt) {
}
