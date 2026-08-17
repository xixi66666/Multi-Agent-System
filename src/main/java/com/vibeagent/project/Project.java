package com.vibeagent.project;

import java.time.Instant;
import java.util.UUID;

public record Project(
        UUID id,
        String name,
        String rootPath,
        ProjectType type,
        Instant createdAt,
        Instant updatedAt) {
}
