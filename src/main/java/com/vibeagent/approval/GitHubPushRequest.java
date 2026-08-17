package com.vibeagent.approval;

public record GitHubPushRequest(
        String remoteUrl,
        String branchName,
        String workspacePath,
        String targetRef,
        String commitSha,
        String baseRevision,
        String diffSha256) {
}
