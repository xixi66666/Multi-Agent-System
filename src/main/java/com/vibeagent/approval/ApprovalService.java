package com.vibeagent.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeagent.event.RunEventService;
import com.vibeagent.run.CoordinatorAgent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ApprovalService {

    private final ApprovalStore approvalStore;
    private final RunEventService runEventService;
    private final CoordinatorAgent coordinatorAgent;
    private final ObjectMapper objectMapper;
    private final Map<String, ApprovalActionExecutor> executors;

    public ApprovalService(
            ApprovalStore approvalStore,
            RunEventService runEventService,
            CoordinatorAgent coordinatorAgent,
            ObjectMapper objectMapper,
            List<ApprovalActionExecutor> executors) {
        this.approvalStore = approvalStore;
        this.runEventService = runEventService;
        this.coordinatorAgent = coordinatorAgent;
        this.objectMapper = objectMapper;
        this.executors = executors.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                ApprovalActionExecutor::actionType,
                executor -> executor));
    }

    public Approval request(UUID runId, UUID taskId, String actionType, Object payload) {
        coordinatorAgent.get(runId);
        Approval approval = approvalStore.create(runId, taskId, actionType, toJson(payload));
        runEventService.publish(runId, "approval.requested", Map.of(
                "approvalId", approval.id(),
                "actionType", actionType,
                "status", approval.status().name()));
        return approval;
    }

    public List<Approval> list(UUID runId) {
        coordinatorAgent.get(runId);
        return approvalStore.findByRun(runId);
    }

    public Approval approve(UUID runId, UUID approvalId, String note) {
        Approval approved = decide(runId, approvalId, ApprovalStatus.APPROVED, note);
        ApprovalActionExecutor executor = executors.get(approved.actionType());
        if (executor == null) {
            return approved;
        }
        try {
            executor.execute(approved);
            Approval executed = approvalStore.finish(approved.id(), ApprovalStatus.EXECUTED, note);
            runEventService.publish(runId, "approval.executed", Map.of(
                    "approvalId", executed.id(),
                    "actionType", executed.actionType(),
                    "status", executed.status().name()));
            return executed;
        } catch (RuntimeException exception) {
            approvalStore.finish(approved.id(), ApprovalStatus.FAILED, "Approved action failed");
            runEventService.publish(runId, "approval.execution.failed", Map.of(
                    "approvalId", approved.id(),
                    "actionType", approved.actionType(),
                    "failureType", exception.getClass().getSimpleName()));
            throw exception;
        }
    }

    public Approval reject(UUID runId, UUID approvalId, String note) {
        return decide(runId, approvalId, ApprovalStatus.REJECTED, note);
    }

    private Approval decide(UUID runId, UUID approvalId, ApprovalStatus status, String note) {
        coordinatorAgent.get(runId);
        Approval approval = approvalStore.find(approvalId)
                .orElseThrow(() -> new IllegalStateException("Approval not found: " + approvalId));
        if (!approval.runId().equals(runId)) {
            throw new IllegalStateException("Approval does not belong to run: " + runId);
        }
        Approval decided = approvalStore.decide(approvalId, status, note);
        runEventService.publish(runId, "approval.decided", Map.of(
                "approvalId", decided.id(),
                "actionType", decided.actionType(),
                "status", decided.status().name()));
        return decided;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Approval payload is not serializable", exception);
        }
    }
}
