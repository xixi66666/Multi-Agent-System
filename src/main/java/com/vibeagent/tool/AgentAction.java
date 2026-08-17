package com.vibeagent.tool;

public record AgentAction(
        ToolAction action,
        String path,
        String url,
        String query,
        String content,
        String expectedSha256,
        AllowedCommand command,
        String summary) {
}
