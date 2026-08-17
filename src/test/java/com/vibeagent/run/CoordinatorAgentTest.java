package com.vibeagent.run;

import com.vibeagent.agent.AgentContext;
import com.vibeagent.agent.AgentResult;
import com.vibeagent.agent.AgentRole;
import com.vibeagent.agent.SpecializedAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinatorAgentTest {

    private final ExecutorService orchestrationExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService agentExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutors() {
        orchestrationExecutor.close();
        agentExecutor.close();
    }

    @Test
    void runsFrontendAndBackendBeforeTestAgent() throws InterruptedException {
        AtomicBoolean testReceivedImplementationResults = new AtomicBoolean();
        SpecializedAgent frontend = successfulAgent(AgentRole.FRONTEND);
        SpecializedAgent backend = successfulAgent(AgentRole.BACKEND);
        SpecializedAgent test = new SpecializedAgent() {
            @Override
            public AgentRole role() {
                return AgentRole.TEST;
            }

            @Override
            public AgentResult execute(AgentContext context) {
                testReceivedImplementationResults.set(
                        context.priorResults().containsKey(AgentRole.FRONTEND)
                                && context.priorResults().containsKey(AgentRole.BACKEND));
                return AgentResult.success(role(), "tested");
            }
        };

        CoordinatorAgent coordinator = new CoordinatorAgent(
                List.of(frontend, backend, test),
                new RunStore(),
                orchestrationExecutor,
                agentExecutor);

        UUID runId = coordinator.start("Build login", "D:/workspace").id();
        RunSnapshot snapshot = awaitTerminalState(coordinator, runId);

        assertThat(snapshot.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(snapshot.results()).containsOnlyKeys(AgentRole.FRONTEND, AgentRole.BACKEND, AgentRole.TEST);
        assertThat(testReceivedImplementationResults).isTrue();
    }

    private RunSnapshot awaitTerminalState(CoordinatorAgent coordinator, UUID runId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        RunSnapshot snapshot;
        do {
            snapshot = coordinator.get(runId);
            if (snapshot.status() == RunStatus.COMPLETED || snapshot.status() == RunStatus.FAILED) {
                return snapshot;
            }
            Thread.sleep(10);
        } while (Instant.now().isBefore(deadline));
        return snapshot;
    }

    private SpecializedAgent successfulAgent(AgentRole role) {
        return new SpecializedAgent() {
            @Override
            public AgentRole role() {
                return role;
            }

            @Override
            public AgentResult execute(AgentContext context) {
                return AgentResult.success(role, role.name().toLowerCase());
            }
        };
    }
}
