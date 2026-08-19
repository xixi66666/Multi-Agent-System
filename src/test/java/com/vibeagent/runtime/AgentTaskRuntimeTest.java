package com.vibeagent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeagent.agent.AgentRole;
import com.vibeagent.collaboration.AgentMessageStore;
import com.vibeagent.event.RunEventService;
import com.vibeagent.model.ModelGateway;
import com.vibeagent.model.ModelRequest;
import com.vibeagent.model.ModelResponse;
import com.vibeagent.run.RunExecutionGuard;
import com.vibeagent.run.RunSnapshot;
import com.vibeagent.run.RunStatus;
import com.vibeagent.run.RuntimeProperties;
import com.vibeagent.tool.ToolExecutionStore;
import com.vibeagent.tool.ToolPolicyViolationException;
import com.vibeagent.tool.ToolResult;
import com.vibeagent.tool.WorkspaceToolGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskRuntimeTest {

    @TempDir
    Path workspace;

    @Test
    void reportsToolPolicyFailureToAgentAndAllowsCorrection() {
        Fixture fixture = fixture(3);
        AgentTask pending = task(AgentTaskStatus.PENDING, 0);
        AgentTask running = task(pending, AgentTaskStatus.RUNNING, 1, null);
        AgentTask completed = task(pending, AgentTaskStatus.COMPLETED, 1, null);

        when(fixture.taskStore.start(eq(pending.id()), anyString(), any(Duration.class)))
                .thenReturn(running);
        when(fixture.modelGateway.generate(any(ModelRequest.class))).thenReturn(
                response("{\"action\":\"LIST_FILES\",\"path\":\"D:/absolute/workspace\"}"),
                response("{\"action\":\"COMPLETE\",\"summary\":\"Corrected the path and completed the task.\"}"));
        when(fixture.workspaceGateway.execute(eq(pending.runId()), eq(running), eq(workspace), any()))
                .thenThrow(new ToolPolicyViolationException("Tool paths must be relative to the task workspace"));
        when(fixture.taskStore.complete(pending.id(), "Corrected the path and completed the task."))
                .thenReturn(completed);

        AgentTaskExecution execution = fixture.runtime.execute(pending, run(pending.runId()), "shared");

        assertThat(execution.task().status()).isEqualTo(AgentTaskStatus.COMPLETED);
        verify(fixture.taskStore, never()).fail(eq(pending.id()), anyString());
        ArgumentCaptor<ModelRequest> requests = ArgumentCaptor.forClass(ModelRequest.class);
        verify(fixture.modelGateway, times(2)).generate(requests.capture());
        assertThat(requests.getAllValues().getFirst().prompt())
                .contains("Tool paths must be relative to the task workspace")
                .contains("use . for the workspace root");
        assertThat(requests.getAllValues().get(1).prompt())
                .contains("Tool action rejected")
                .contains("Tool paths must be relative to the task workspace");
    }

    @Test
    void retriesTaskAfterToolTurnsAreExhausted() {
        Fixture fixture = fixture(1);
        AgentTask pending = task(AgentTaskStatus.PENDING, 0);
        AgentTask runningAttemptOne = task(pending, AgentTaskStatus.RUNNING, 1, null);
        AgentTask failedAttemptOne = task(
                pending,
                AgentTaskStatus.FAILED,
                1,
                "Agent exceeded the maximum tool turns without completing its task");
        AgentTask pendingAttemptTwo = task(pending, AgentTaskStatus.PENDING, 1, null);
        AgentTask runningAttemptTwo = task(pending, AgentTaskStatus.RUNNING, 2, null);
        AgentTask completedAttemptTwo = task(pending, AgentTaskStatus.COMPLETED, 2, null);

        when(fixture.taskStore.start(eq(pending.id()), anyString(), any(Duration.class)))
                .thenReturn(runningAttemptOne, runningAttemptTwo);
        when(fixture.modelGateway.generate(any(ModelRequest.class))).thenReturn(
                response("{\"action\":\"LIST_FILES\",\"path\":\".\"}"),
                response("{\"action\":\"COMPLETE\",\"summary\":\"Finished on the second attempt.\"}"));
        when(fixture.workspaceGateway.execute(eq(pending.runId()), eq(runningAttemptOne), eq(workspace), any()))
                .thenReturn(new ToolResult(true, "README.md", Map.of("count", 1)));
        when(fixture.taskStore.fail(
                pending.id(), "Agent exceeded the maximum tool turns without completing its task"))
                .thenReturn(failedAttemptOne);
        when(fixture.taskStore.retry(pending.id())).thenReturn(pendingAttemptTwo);
        when(fixture.taskStore.complete(pending.id(), "Finished on the second attempt."))
                .thenReturn(completedAttemptTwo);

        AgentTaskExecution execution = fixture.runtime.execute(pending, run(pending.runId()), "shared");

        assertThat(execution.task().attempt()).isEqualTo(2);
        assertThat(execution.task().status()).isEqualTo(AgentTaskStatus.COMPLETED);
        verify(fixture.taskStore, times(2))
                .start(eq(pending.id()), anyString(), any(Duration.class));
        verify(fixture.taskStore).fail(
                pending.id(), "Agent exceeded the maximum tool turns without completing its task");
        verify(fixture.taskStore).retry(pending.id());
    }

    @Test
    void retriesStructuredResponseBeforeCompletingTask() {
        Fixture fixture = fixture(3);
        AgentTask pending = task(AgentTaskStatus.PENDING, 0);
        AgentTask runningAttemptOne = task(pending, AgentTaskStatus.RUNNING, 1, null);
        AgentTask failedAttemptOne = task(
                pending,
                AgentTaskStatus.FAILED,
                1,
                "Planner did not return a valid execution plan: missing root object terminator");
        AgentTask pendingAttemptTwo = task(pending, AgentTaskStatus.PENDING, 1, null);
        AgentTask runningAttemptTwo = task(pending, AgentTaskStatus.RUNNING, 2, null);
        AgentTask failedFinalAttempt = task(pending, AgentTaskStatus.FAILED, 3, "unexpected second failure");
        AgentTask completedAttemptTwo = task(pending, AgentTaskStatus.COMPLETED, 2, null);
        String malformed = "{\"summary\":\"plan\",\"implementationTasks\":[]";
        String valid = "{\"summary\":\"plan\",\"implementationTasks\":[]}";

        when(fixture.taskStore.start(eq(pending.id()), anyString(), any(Duration.class)))
                .thenReturn(runningAttemptOne, runningAttemptTwo);
        when(fixture.modelGateway.generate(any(ModelRequest.class))).thenReturn(
                completeResponse(malformed), completeResponse(valid));
        when(fixture.taskStore.fail(eq(pending.id()), anyString()))
                .thenReturn(failedAttemptOne, failedFinalAttempt);
        when(fixture.taskStore.retry(pending.id())).thenReturn(pendingAttemptTwo);
        when(fixture.taskStore.complete(eq(pending.id()), anyString())).thenReturn(completedAttemptTwo);

        AgentTaskExecution execution = fixture.runtime.execute(
                pending,
                run(pending.runId()),
                "shared",
                content -> {
                    if (!content.endsWith("}")) {
                        throw new IllegalStateException(
                                "Planner did not return a valid execution plan: missing root object terminator");
                    }
                    return content;
                });

        assertThat(execution.task().attempt()).isEqualTo(2);
        assertThat(execution.modelResponse().content()).isEqualTo(valid);
        verify(fixture.taskStore, times(2))
                .start(eq(pending.id()), anyString(), any(Duration.class));
        ArgumentCaptor<String> completedContent = ArgumentCaptor.forClass(String.class);
        verify(fixture.taskStore).complete(eq(pending.id()), completedContent.capture());
        assertThat(completedContent.getValue()).isEqualTo(valid);
    }

    private Fixture fixture(int maxToolTurns) {
        AgentTaskStore taskStore = mock(AgentTaskStore.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        RunEventService eventService = mock(RunEventService.class);
        WorkspaceToolGateway workspaceGateway = mock(WorkspaceToolGateway.class);
        ToolExecutionStore executionStore = mock(ToolExecutionStore.class);
        AgentMessageStore messageStore = mock(AgentMessageStore.class);
        RunExecutionGuard executionGuard = mock(RunExecutionGuard.class);
        RuntimeProperties properties = new RuntimeProperties();
        properties.setMaxToolTurns(maxToolTurns);
        AgentTaskRuntime runtime = new AgentTaskRuntime(
                taskStore,
                new AgentDefinitionRegistry(),
                modelGateway,
                eventService,
                workspaceGateway,
                new ObjectMapper(),
                executionStore,
                messageStore,
                executionGuard,
                properties,
                new StructuredActionParser());
        return new Fixture(taskStore, modelGateway, workspaceGateway, runtime);
    }

    private RunSnapshot run(UUID runId) {
        Instant now = Instant.now();
        return new RunSnapshot(
                runId,
                UUID.randomUUID(),
                "Implement a feature",
                workspace.toString(),
                RunStatus.IMPLEMENTING,
                Map.of(),
                null,
                null,
                now,
                now);
    }

    private AgentTask task(AgentTaskStatus status, int attempt) {
        Instant now = Instant.now();
        return new AgentTask(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                AgentRole.IMPLEMENTER,
                "backend",
                "Implement feature",
                "Implement the assigned feature.",
                status,
                attempt,
                3,
                status == AgentTaskStatus.RUNNING ? "worker" : null,
                status == AgentTaskStatus.RUNNING ? now.plusSeconds(60) : null,
                null,
                null,
                now,
                now);
    }

    private AgentTask task(AgentTask source, AgentTaskStatus status, int attempt, String failure) {
        Instant now = Instant.now();
        return new AgentTask(
                source.id(),
                source.runId(),
                source.parentTaskId(),
                source.role(),
                source.specialty(),
                source.title(),
                source.instructions(),
                status,
                attempt,
                source.maxAttempts(),
                status == AgentTaskStatus.RUNNING ? "worker" : null,
                status == AgentTaskStatus.RUNNING ? now.plusSeconds(60) : null,
                status == AgentTaskStatus.COMPLETED ? "done" : null,
                failure,
                source.createdAt(),
                now);
    }

    private ModelResponse response(String content) {
        return new ModelResponse(
                content,
                "test-provider",
                "test-model",
                "stop",
                10,
                5,
                0,
                0,
                15,
                BigDecimal.ZERO,
                false,
                1);
    }

    private ModelResponse completeResponse(String summary) {
        try {
            return response(new ObjectMapper().writeValueAsString(Map.of(
                    "action", "COMPLETE",
                    "summary", summary)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Could not prepare COMPLETE response", exception);
        }
    }

    private record Fixture(
            AgentTaskStore taskStore,
            ModelGateway modelGateway,
            WorkspaceToolGateway workspaceGateway,
            AgentTaskRuntime runtime) {
    }
}
