package com.vibeagent.tool;

import java.util.Map;

public record ToolResult(
        boolean successful,
        String output,
        Map<String, Object> metadata) {

    public ToolResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
