package com.vibeagent.run;

import com.vibeagent.agent.AgentRole;
import com.vibeagent.model.ModelGateway;
import com.vibeagent.model.ModelRequest;
import com.vibeagent.model.ModelResponse;
import com.vibeagent.runtime.AgentTaskStore;
import com.vibeagent.tool.ToolExecution;
import com.vibeagent.tool.ToolExecutionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "vibe.models.enabled=true")
@Import(ReviewRepairWorkflowTest.FakeModelConfiguration.class)
class ReviewRepairWorkflowTest {

    @Autowired
    private CoordinatorAgent coordinatorAgent;

    @Autowired
    private AgentTaskStore agentTaskStore;

    @Test
    void repairsAndRetestsAfterInitialReviewRejection() throws InterruptedException {
        UUID runId = coordinatorAgent.start("Add a verified endpoint", "D:/workspace").id();

        RunSnapshot snapshot = awaitTerminalState(runId);

        assertThat(snapshot.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(agentTaskStore.findByRun(runId).stream()
                .filter(task -> task.role() == AgentRole.REVIEWER)).hasSize(2);
        assertThat(agentTaskStore.findByRun(runId).stream()
                .filter(task -> task.role() == AgentRole.IMPLEMENTER)).hasSize(2);
        assertThat(agentTaskStore.findByRun(runId).stream()
                .filter(task -> task.role() == AgentRole.TESTER)).hasSize(2);
    }

    private RunSnapshot awaitTerminalState(UUID runId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        RunSnapshot snapshot;
        do {
            snapshot = coordinatorAgent.get(runId);
            if (snapshot.status().isTerminal() || snapshot.status() == RunStatus.NEEDS_ATTENTION) {
                return snapshot;
            }
            Thread.sleep(10);
        } while (Instant.now().isBefore(deadline));
        return snapshot;
    }

    @TestConfiguration
    static class FakeModelConfiguration {

        @Bean
        @Primary
        ModelGateway repairWorkflowModel(ToolExecutionStore toolExecutionStore) {
            AtomicInteger reviews = new AtomicInteger();
            return request -> {
                String content = switch (request.role()) {
                    case PLANNER -> "{\"summary\":\"plan\",\"requiresArchitecture\":false,"
                            + "\"requiresResearch\":false,\"implementationTasks\":[{\"title\":\"Implement\","
                            + "\"specialty\":\"backend\",\"instructions\":\"Implement endpoint\"}],"
                            + "\"acceptanceCriteria\":[\"Tests pass\"]}";
                    case IMPLEMENTER -> complete("Implementation completed");
                    case TESTER -> {
                        toolExecutionStore.record(new ToolExecution(
                                UUID.randomUUID(), request.runId(), request.taskId(),
                                "RUN_COMMAND:MAVEN_TEST", "SUCCESS", 0, 1, Instant.now()));
                        yield complete("Verification passed");
                    }
                    case REVIEWER -> reviews.incrementAndGet() == 1
                            ? "{\"approved\":false,\"summary\":\"repair required\","
                                    + "\"findings\":[\"Add the missing edge case\"]}"
                            : "{\"approved\":true,\"summary\":\"approved\",\"findings\":[]}";
                    default -> "Task completed";
                };
                return new ModelResponse(
                        content, "test-provider", "deterministic-test", 10, 5, 0, 0, 15,
                        BigDecimal.ZERO, false, 1);
            };
        }

        private String complete(String summary) {
            return "{\"action\":\"COMPLETE\",\"summary\":\"" + summary + "\"}";
        }
    }
}
