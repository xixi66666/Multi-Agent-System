package com.vibeagent.run;

import com.vibeagent.agent.AgentRole;
import com.vibeagent.runtime.AgentTask;
import com.vibeagent.runtime.AgentTaskStatus;
import com.vibeagent.runtime.AgentTaskStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentTaskRetryPersistenceTest {

    @Autowired
    private RunStore runStore;

    @Autowired
    private AgentTaskStore taskStore;

    @Test
    void failedTaskCanReturnToPendingAndStartItsNextAttempt() {
        CodingRun run = runStore.create("Retry a failed task", "D:/workspace");
        AgentTask pending = taskStore.create(
                run.id(),
                null,
                AgentRole.IMPLEMENTER,
                "backend",
                "Implement retryable work",
                "Implement the assigned change.",
                3);

        AgentTask firstAttempt = taskStore.start(pending.id(), "worker-1", Duration.ofMinutes(1));
        AgentTask failed = taskStore.fail(firstAttempt.id(), "temporary failure");
        AgentTask retryPending = taskStore.retry(failed.id());
        AgentTask secondAttempt = taskStore.start(retryPending.id(), "worker-2", Duration.ofMinutes(1));

        assertThat(firstAttempt.attempt()).isEqualTo(1);
        assertThat(failed.status()).isEqualTo(AgentTaskStatus.FAILED);
        assertThat(retryPending.status()).isEqualTo(AgentTaskStatus.PENDING);
        assertThat(retryPending.failure()).isNull();
        assertThat(secondAttempt.status()).isEqualTo(AgentTaskStatus.RUNNING);
        assertThat(secondAttempt.attempt()).isEqualTo(2);
        assertThat(secondAttempt.leaseOwner()).isEqualTo("worker-2");
    }
}
