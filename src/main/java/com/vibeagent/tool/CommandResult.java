package com.vibeagent.tool;

public record CommandResult(
        int exitCode,
        String output,
        long durationMillis) {
}
