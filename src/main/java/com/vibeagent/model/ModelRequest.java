package com.vibeagent.model;

import com.vibeagent.agent.AgentRole;

import java.util.UUID;

public record ModelRequest(
        UUID runId,
        UUID taskId,
        AgentRole role,
        String systemInstruction,
        String prompt) {
}
