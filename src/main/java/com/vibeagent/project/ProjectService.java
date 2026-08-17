package com.vibeagent.project;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectStore projectStore;
    private final WorkspacePathPolicy pathPolicy;
    private final LocalGitClient gitClient;

    public ProjectService(
            ProjectStore projectStore,
            WorkspacePathPolicy pathPolicy,
            LocalGitClient gitClient) {
        this.projectStore = projectStore;
        this.pathPolicy = pathPolicy;
        this.gitClient = gitClient;
    }

    public Project register(String name, String rootPath, ProjectType type) {
        requireName(name);
        Path path = switch (type) {
            case EXISTING_GIT -> registerExisting(rootPath);
            case NEW_DIRECTORY -> createNew(rootPath);
        };
        return projectStore.findByRootPath(path.toString())
                .orElseGet(() -> projectStore.create(name.trim(), path.toString(), type));
    }

    public Project get(UUID id) {
        return projectStore.find(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    public List<Project> list() {
        return projectStore.findAll();
    }

    private Path registerExisting(String rootPath) {
        Path path = pathPolicy.requireExistingDirectory(rootPath);
        if (!gitClient.isRepository(path)) {
            throw new InvalidWorkspaceException("Existing project must be a Git work tree");
        }
        return path;
    }

    private Path createNew(String rootPath) {
        Path path = pathPolicy.prepareEmptyDirectory(rootPath);
        gitClient.initialize(path);
        return path;
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidWorkspaceException("Project name is required");
        }
        if (name.length() > 255) {
            throw new InvalidWorkspaceException("Project name is too long");
        }
    }
}
