package com.vibeagent.approval;

import com.vibeagent.project.LocalGitClient;
import com.vibeagent.run.CoordinatorAgent;
import com.vibeagent.run.RunSnapshot;
import com.vibeagent.workspace.TaskWorkspace;
import com.vibeagent.workspace.TaskWorkspaceStore;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class GitHubApprovalService {

    private final CoordinatorAgent coordinatorAgent;
    private final TaskWorkspaceStore workspaceStore;
    private final LocalGitClient gitClient;
    private final ApprovalService approvalService;

    public GitHubApprovalService(
            CoordinatorAgent coordinatorAgent,
            TaskWorkspaceStore workspaceStore,
            LocalGitClient gitClient,
            ApprovalService approvalService) {
        this.coordinatorAgent = coordinatorAgent;
        this.workspaceStore = workspaceStore;
        this.gitClient = gitClient;
        this.approvalService = approvalService;
    }

    public Approval preparePush(UUID runId) {
        RunSnapshot run = coordinatorAgent.get(runId);
        boolean completed = run.status() == com.vibeagent.run.RunStatus.COMPLETED
                || run.status() == com.vibeagent.run.RunStatus.COMPLETED_WITH_WARNINGS;
        if (run.projectId() == null || !completed) {
            throw new IllegalStateException("GitHub push can only be prepared for a completed registered-project run");
        }
        TaskWorkspace workspace = workspaceStore.findIntegration(runId)
                .orElseThrow(() -> new IllegalStateException("Run has no integration worktree"));
        String remoteUrl = gitClient.originUrl(Path.of(workspace.path()));
        if (!isGitHubRemote(remoteUrl)) {
            throw new IllegalStateException("Git origin is not a GitHub remote");
        }
        String commitSha = gitClient.headRevision(Path.of(workspace.path()));
        String diffSha256 = sha256(gitClient.diffAgainst(
                Path.of(workspace.path()), workspace.baseRevision()));
        GitHubPushRequest request = new GitHubPushRequest(
                remoteUrl,
                workspace.branchName(),
                workspace.path(),
                "refs/heads/" + workspace.branchName(),
                commitSha,
                workspace.baseRevision(),
                diffSha256);
        return approvalService.request(runId, null, GitHubPushExecutor.ACTION_TYPE, request);
    }

    private boolean isGitHubRemote(String remoteUrl) {
        String normalized = remoteUrl.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("https://github.com/") || normalized.startsWith("git@github.com:");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
