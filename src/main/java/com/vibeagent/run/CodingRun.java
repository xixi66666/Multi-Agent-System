package com.vibeagent.run;

import com.vibeagent.agent.AgentResult;
import com.vibeagent.agent.AgentRole;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

final class CodingRun {

    private final UUID id;
    private final UUID projectId;
    private final String requirement;
    private String workspace;
    private final Instant createdAt;
    private final Map<AgentRole, AgentResult> results = new EnumMap<>(AgentRole.class);
    private RunStatus status = RunStatus.PLANNING;
    private String summary;
    private String failure;
    private Instant updatedAt;

    CodingRun(String requirement, String workspace) {
        this(requirement, workspace, null);
    }

    CodingRun(String requirement, String workspace, UUID projectId) {
        this(UUID.randomUUID(), projectId, requirement, workspace, RunStatus.CREATED, null, null, Instant.now(), Instant.now(), Map.of());
    }

    private CodingRun(
            UUID id,
            UUID projectId,
            String requirement,
            String workspace,
            RunStatus status,
            String summary,
            String failure,
            Instant createdAt,
            Instant updatedAt,
            Map<AgentRole, AgentResult> results) {
        this.id = id;
        this.projectId = projectId;
        this.requirement = requirement;
        this.workspace = workspace;
        this.status = status;
        this.summary = summary;
        this.failure = failure;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.results.putAll(results);
    }

    static CodingRun restore(
            UUID id,
            UUID projectId,
            String requirement,
            String workspace,
            RunStatus status,
            String summary,
            String failure,
            Instant createdAt,
            Instant updatedAt,
            Map<AgentRole, AgentResult> results) {
        return new CodingRun(id, projectId, requirement, workspace, status, summary, failure, createdAt, updatedAt, results);
    }

    synchronized UUID id() {
        return id;
    }

    synchronized String requirement() {
        return requirement;
    }

    synchronized String workspace() {
        return workspace;
    }

    synchronized UUID projectId() {
        return projectId;
    }

    synchronized void useWorkspace(String workspace) {
        this.workspace = workspace;
        this.updatedAt = Instant.now();
    }

    synchronized void transitionTo(RunStatus nextStatus) {
        this.status = nextStatus;
        this.updatedAt = Instant.now();
    }

    synchronized void addResult(AgentResult result) {
        results.put(result.role(), result);
        updatedAt = Instant.now();
    }

    synchronized Map<AgentRole, AgentResult> results() {
        return Map.copyOf(results);
    }

    synchronized void complete(String summary) {
        this.summary = summary;
        transitionTo(RunStatus.COMPLETED);
    }

    synchronized void completeWithWarnings(String summary) {
        this.summary = summary;
        transitionTo(RunStatus.COMPLETED_WITH_WARNINGS);
    }

    synchronized void needsAttention(String summary) {
        this.summary = summary;
        transitionTo(RunStatus.NEEDS_ATTENTION);
    }

    synchronized void fail(Throwable throwable) {
        fail(throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage());
    }

    synchronized void fail(String failure) {
        this.failure = failure;
        transitionTo(RunStatus.FAILED);
    }

    synchronized RunSnapshot snapshot() {
        return new RunSnapshot(
                id,
                projectId,
                requirement,
                workspace,
                status,
                Map.copyOf(results),
                summary,
                failure,
                createdAt,
                updatedAt);
    }
}
