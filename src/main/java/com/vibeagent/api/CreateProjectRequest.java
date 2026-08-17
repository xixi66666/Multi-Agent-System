package com.vibeagent.api;

import com.vibeagent.project.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProjectRequest(
        @NotBlank String name,
        @NotBlank String rootPath,
        @NotNull ProjectType type) {
}
