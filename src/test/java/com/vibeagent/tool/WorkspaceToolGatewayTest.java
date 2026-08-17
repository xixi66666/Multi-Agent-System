package com.vibeagent.tool;

import com.vibeagent.agent.AgentRole;
import com.vibeagent.event.RunEventService;
import com.vibeagent.runtime.AgentTask;
import com.vibeagent.runtime.AgentTaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkspaceToolGatewayTest {

    @TempDir
    Path workspace;

    @Test
    void writesAndReadsInsideWorkspaceButRejectsPathEscape() throws Exception {
        ToolExecutionStore executionStore = mock(ToolExecutionStore.class);
        RunEventService eventService = mock(RunEventService.class);
        WorkspaceToolGateway gateway = new WorkspaceToolGateway(
                mock(BoundedCommandRunner.class), executionStore, eventService,
                new SensitiveDataRedactor(), mock(WebDocumentReader.class));
        UUID runId = UUID.randomUUID();
        AgentTask task = implementerTask(runId);

        ToolResult written = gateway.execute(runId, task, workspace, new AgentAction(
                ToolAction.WRITE_FILE,
                "src/example.txt",
                null,
                null,
                "safe content\n",
                null,
                null,
                null));
        ToolResult read = gateway.execute(runId, task, workspace, new AgentAction(
                ToolAction.READ_FILE,
                "src/example.txt",
                null,
                null,
                null,
                null,
                null,
                null));

        assertThat(written.successful()).isTrue();
        assertThat(read.output()).isEqualTo("safe content\n");
        assertThat(read.metadata()).containsKeys("sha256", "sizeBytes");
        assertThat(Files.readString(workspace.resolve("src/example.txt"))).isEqualTo("safe content\n");
        verify(executionStore, org.mockito.Mockito.times(2)).record(any());

        assertThatThrownBy(() -> gateway.execute(runId, task, workspace, new AgentAction(
                ToolAction.READ_FILE,
                "../outside.txt",
                null,
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(ToolPolicyViolationException.class)
                .hasMessageContaining("escaped");
        verify(executionStore, org.mockito.Mockito.times(3)).record(any());
    }

    private AgentTask implementerTask(UUID runId) {
        Instant now = Instant.now();
        return new AgentTask(
                UUID.randomUUID(),
                runId,
                null,
                AgentRole.IMPLEMENTER,
                "general",
                "Implement",
                "Implement safely",
                AgentTaskStatus.RUNNING,
                1,
                3,
                "test-worker",
                now.plusSeconds(60),
                null,
                null,
                now,
                now);
    }
}
