package com.vibeagent.run;

import com.vibeagent.agent.AgentRole;
import com.vibeagent.collaboration.AgentMessageStore;
import com.vibeagent.model.ModelUsageStore;
import com.vibeagent.runtime.AgentTaskStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AutonomousWorkflowTest {

    @Autowired
    private CoordinatorAgent coordinatorAgent;

    @Autowired
    private AgentTaskStore agentTaskStore;

    @Autowired
    private ModelUsageStore modelUsageStore;

    @Autowired
    private AgentMessageStore agentMessageStore;

    @Test
    void runsPersistedAutonomousWorkflowAndRecordsUsage() throws InterruptedException {
        UUID runId = coordinatorAgent.start("Add a health endpoint", "D:/workspace").id();

        RunSnapshot snapshot = awaitTerminalState(runId);

        assertThat(snapshot.status()).isEqualTo(RunStatus.COMPLETED_WITH_WARNINGS);
        assertThat(agentTaskStore.findByRun(runId))
                .extracting(task -> task.role())
                .containsExactly(
                        AgentRole.PLANNER,
                        AgentRole.IMPLEMENTER,
                        AgentRole.INTEGRATOR,
                        AgentRole.TESTER,
                        AgentRole.REVIEWER);
        assertThat(modelUsageStore.summary(runId).calls()).isEqualTo(5);
        assertThat(modelUsageStore.summary(runId).totalTokens()).isPositive();
        assertThat(agentMessageStore.findByRun(runId)).hasSize(5);
    }

    private RunSnapshot awaitTerminalState(UUID runId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        RunSnapshot snapshot;
        do {
            snapshot = coordinatorAgent.get(runId);
            if (snapshot.status().isTerminal()) {
                return snapshot;
            }
            Thread.sleep(10);
        } while (Instant.now().isBefore(deadline));
        return snapshot;
    }
}
