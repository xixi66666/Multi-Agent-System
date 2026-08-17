package com.vibeagent.workspace;

import com.vibeagent.project.LocalGitClient;
import com.vibeagent.project.Project;
import com.vibeagent.project.ProjectService;
import com.vibeagent.project.WorkspaceProperties;
import com.vibeagent.runtime.AgentTask;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class WorktreeService {

    private final ProjectService projectService;
    private final LocalGitClient gitClient;
    private final TaskWorkspaceStore workspaceStore;
    private final Path worktreeRoot;

    public WorktreeService(
            ProjectService projectService,
            LocalGitClient gitClient,
            TaskWorkspaceStore workspaceStore,
            WorkspaceProperties properties) {
        this.projectService = projectService;
        this.gitClient = gitClient;
        this.workspaceStore = workspaceStore;
        this.worktreeRoot = Path.of(properties.getWorktreeRoot()).toAbsolutePath().normalize();
    }

    public TaskWorkspace prepareIntegration(UUID runId, UUID projectId) {
        return workspaceStore.findIntegration(runId).orElseGet(() -> {
            Project project = projectService.get(projectId);
            Path runRoot = ownedPath(runId.toString());
            Path integrationPath = runRoot.resolve("integration").normalize();
            prepareParent(integrationPath);
            String branch = "vibe/run/" + compact(runId);
            String baseRevision = gitClient.headRevision(Path.of(project.rootPath()));
            gitClient.createWorktree(Path.of(project.rootPath()), integrationPath, branch, "HEAD");
            return workspaceStore.create(
                    runId, null, WorkspaceType.INTEGRATION, integrationPath.toString(), branch, baseRevision);
        });
    }

    public TaskWorkspace prepareTask(UUID projectId, TaskWorkspace integration, AgentTask task) {
        return workspaceStore.findByTask(task.id()).orElseGet(() -> {
            Project project = projectService.get(projectId);
            Path taskPath = ownedPath(task.runId().toString(), "tasks", compact(task.id()));
            prepareParent(taskPath);
            String branch = integration.branchName() + "/task/" + compact(task.id());
            String baseRevision = gitClient.headRevision(Path.of(integration.path()));
            gitClient.createWorktree(
                    Path.of(project.rootPath()), taskPath, branch, integration.branchName());
            return workspaceStore.create(
                    task.runId(), task.id(), WorkspaceType.TASK, taskPath.toString(), branch, baseRevision);
        });
    }

    public void integrate(TaskWorkspace integration, TaskWorkspace taskWorkspace, AgentTask task) {
        Path taskPath = Path.of(taskWorkspace.path());
        boolean committed = gitClient.commitChanges(taskPath, "Agent task: " + task.title());
        if (committed) {
            gitClient.merge(
                    Path.of(integration.path()),
                    taskWorkspace.branchName(),
                    "Integrate agent task: " + task.title());
        }
    }

    public void commitIntegration(TaskWorkspace integration, String message) {
        gitClient.commitChanges(Path.of(integration.path()), message);
    }

    public String finalDiff(TaskWorkspace integration) {
        return gitClient.diffAgainst(Path.of(integration.path()), integration.baseRevision());
    }

    public java.util.List<TaskWorkspace> list(UUID runId) {
        return workspaceStore.findByRun(runId);
    }

    private Path ownedPath(String first, String... more) {
        Path path = worktreeRoot.resolve(Path.of(first, more)).normalize();
        if (!path.startsWith(worktreeRoot)) {
            throw new IllegalStateException("Worktree path escaped the configured root");
        }
        return path;
    }

    private void prepareParent(Path path) {
        if (Files.exists(path)) {
            throw new IllegalStateException("Worktree target already exists: " + path);
        }
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Worktree parent directory cannot be created", exception);
        }
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "");
    }
}
