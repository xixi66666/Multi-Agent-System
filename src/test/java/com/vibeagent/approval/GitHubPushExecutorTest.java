package com.vibeagent.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeagent.project.LocalGitClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubPushExecutorTest {

    @Test
    void rejectsPushWhenApprovedCommitChanged() throws Exception {
        LocalGitClient gitClient = mock(LocalGitClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GitHubPushRequest request = new GitHubPushRequest(
                "https://github.com/example/project.git",
                "vibe/run/test",
                Path.of(".").toAbsolutePath().normalize().toString(),
                "refs/heads/vibe/run/test",
                "1111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000",
                "ignored");
        Approval approval = new Approval(
                UUID.randomUUID(), UUID.randomUUID(), null, GitHubPushExecutor.ACTION_TYPE,
                objectMapper.writeValueAsString(request), ApprovalStatus.APPROVED, null, Instant.now(), Instant.now());
        Path workspace = Path.of(request.workspacePath()).toAbsolutePath().normalize();
        when(gitClient.originUrl(workspace)).thenReturn(request.remoteUrl());
        when(gitClient.headRevision(workspace)).thenReturn("2222222222222222222222222222222222222222");

        GitHubPushExecutor executor = new GitHubPushExecutor(objectMapper, gitClient);

        assertThatThrownBy(() -> executor.execute(approval))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HEAD changed");
        verify(gitClient, never()).push(workspace, request.branchName());
    }
}
