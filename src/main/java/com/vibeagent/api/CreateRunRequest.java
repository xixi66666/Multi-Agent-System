package com.vibeagent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;

import java.util.UUID;

public record CreateRunRequest(
        @NotBlank String requirement,
        UUID projectId,
        String workspace) {

    @AssertTrue(message = "projectId or workspace is required")
    public boolean hasWorkspace() {
        return projectId != null || (workspace != null && !workspace.isBlank());
    }
}
