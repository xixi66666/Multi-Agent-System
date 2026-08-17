package com.vibeagent.project;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class WorkspacePathPolicy {

    private final List<Path> allowedRoots;

    public WorkspacePathPolicy(WorkspaceProperties properties) {
        this.allowedRoots = properties.getAllowedRoots().stream()
                .map(this::resolveAllowedRoot)
                .toList();
        if (allowedRoots.isEmpty()) {
            throw new IllegalStateException("At least one vibe.workspace.allowed-roots entry is required");
        }
    }

    public Path requireExistingDirectory(String requestedPath) {
        try {
            Path resolved = Path.of(requestedPath).toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(resolved)) {
                throw new InvalidWorkspaceException("Workspace is not a directory: " + resolved);
            }
            requireAllowed(resolved);
            return resolved;
        } catch (IOException exception) {
            throw new InvalidWorkspaceException("Workspace does not exist or cannot be read");
        }
    }

    public Path prepareEmptyDirectory(String requestedPath) {
        Path target = Path.of(requestedPath).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new InvalidWorkspaceException("New project requires a parent directory");
        }
        try {
            Path realParent = parent.toRealPath();
            requireAllowed(realParent);
            Path resolvedTarget = realParent.resolve(target.getFileName()).normalize();
            requireAllowed(resolvedTarget);
            if (Files.exists(resolvedTarget)) {
                if (!Files.isDirectory(resolvedTarget)) {
                    throw new InvalidWorkspaceException("New project path is not a directory");
                }
                try (var entries = Files.list(resolvedTarget)) {
                    if (entries.findAny().isPresent()) {
                        throw new InvalidWorkspaceException("New project directory must be empty");
                    }
                }
            } else {
                Files.createDirectory(resolvedTarget);
            }
            return resolvedTarget.toRealPath();
        } catch (IOException exception) {
            throw new InvalidWorkspaceException("New project directory cannot be prepared");
        }
    }

    private Path resolveAllowedRoot(String value) {
        try {
            return Path.of(value).toAbsolutePath().normalize().toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Configured workspace root does not exist: " + value, exception);
        }
    }

    private void requireAllowed(Path path) {
        boolean allowed = allowedRoots.stream().anyMatch(root -> path.equals(root) || path.startsWith(root));
        if (!allowed) {
            throw new InvalidWorkspaceException("Workspace is outside configured allowed roots");
        }
    }
}
