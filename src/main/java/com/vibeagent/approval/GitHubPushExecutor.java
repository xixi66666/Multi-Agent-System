package com.vibeagent.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeagent.project.LocalGitClient;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GitHubPushExecutor implements ApprovalActionExecutor {

    public static final String ACTION_TYPE = "GITHUB_PUSH";

    private final ObjectMapper objectMapper;
    private final LocalGitClient gitClient;

    public GitHubPushExecutor(ObjectMapper objectMapper, LocalGitClient gitClient) {
        this.objectMapper = objectMapper;
        this.gitClient = gitClient;
    }

    @Override
    public String actionType() {
        return ACTION_TYPE;
    }

    @Override
    public void execute(Approval approval) {
        try {
            GitHubPushRequest request = objectMapper.readValue(approval.requestPayload(), GitHubPushRequest.class);
            Path workspace = Path.of(request.workspacePath()).toAbsolutePath().normalize();
            if (!("refs/heads/" + request.branchName()).equals(request.targetRef())) {
                throw new IllegalStateException("Approved Git target ref is inconsistent");
            }
            String currentRemote = gitClient.originUrl(workspace);
            if (!currentRemote.equals(request.remoteUrl())) {
                throw new IllegalStateException("Git origin changed after approval was requested");
            }
            String currentHead = gitClient.headRevision(workspace);
            if (!currentHead.equals(request.commitSha())) {
                throw new IllegalStateException("Git HEAD changed after approval was requested");
            }
            String currentDiffSha256 = sha256(gitClient.diffAgainst(workspace, request.baseRevision()));
            if (!currentDiffSha256.equals(request.diffSha256())) {
                throw new IllegalStateException("Git diff changed after approval was requested");
            }
            gitClient.push(workspace, request.branchName());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Approved GitHub push payload is invalid", exception);
        }
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
