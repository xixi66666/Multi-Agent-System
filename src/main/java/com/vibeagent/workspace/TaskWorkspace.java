package com.vibeagent.workspace;

import java.time.Instant;
import java.util.UUID;

public record TaskWorkspace(
        UUID id,
        UUID runId,
        UUID taskId,
        WorkspaceType type,
        String path,
        String branchName,
        String baseRevision,
        Instant createdAt) {
}
