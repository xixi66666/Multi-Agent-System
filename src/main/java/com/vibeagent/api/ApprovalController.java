package com.vibeagent.api;

import com.vibeagent.approval.Approval;
import com.vibeagent.approval.ApprovalService;
import com.vibeagent.approval.GitHubApprovalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs/{runId}/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final GitHubApprovalService gitHubApprovalService;

    public ApprovalController(ApprovalService approvalService, GitHubApprovalService gitHubApprovalService) {
        this.approvalService = approvalService;
        this.gitHubApprovalService = gitHubApprovalService;
    }

    @GetMapping
    public List<Approval> list(@PathVariable UUID runId) {
        return approvalService.list(runId);
    }

    @PostMapping("/github-push")
    public Approval prepareGitHubPush(@PathVariable UUID runId) {
        return gitHubApprovalService.preparePush(runId);
    }

    @PostMapping("/{approvalId}/approve")
    public Approval approve(
            @PathVariable UUID runId,
            @PathVariable UUID approvalId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return approvalService.approve(runId, approvalId, request == null ? null : request.note());
    }

    @PostMapping("/{approvalId}/reject")
    public Approval reject(
            @PathVariable UUID runId,
            @PathVariable UUID approvalId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return approvalService.reject(runId, approvalId, request == null ? null : request.note());
    }
}
