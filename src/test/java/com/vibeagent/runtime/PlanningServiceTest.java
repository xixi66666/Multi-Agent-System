package com.vibeagent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PlanningServiceTest {

    private final PlanningService planningService = new PlanningService(
            mock(AgentTaskStore.class),
            mock(AgentTaskRuntime.class),
            new ObjectMapper(),
            mock(RepositoryContextService.class));

    @Test
    void rejectsDuplicateTaskFieldsInsteadOfSilentlyOverwritingTask() {
        String planWithMergedTasks = """
                {
                  "summary": "plan",
                  "requiresArchitecture": false,
                  "requiresResearch": false,
                  "implementationTasks": [{
                    "title": "backend",
                    "specialty": "backend",
                    "instructions": "Implement backend",
                    "title": "frontend",
                    "specialty": "frontend",
                    "instructions": "Implement frontend"
                  }],
                  "acceptanceCriteria": ["Tests pass"]
                }
                """;

        assertThatThrownBy(() -> planningService.parsePlan(planWithMergedTasks))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void rejectsOverlyLongTaskInstructions() {
        String plan = """
                {
                  "summary": "plan",
                  "requiresArchitecture": false,
                  "requiresResearch": false,
                  "implementationTasks": [{
                    "title": "backend",
                    "specialty": "backend",
                    "instructions": "%s"
                  }],
                  "acceptanceCriteria": ["Tests pass"]
                }
                """.formatted("x".repeat(2_001));

        assertThatThrownBy(() -> planningService.parsePlan(plan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("instructions")
                .hasMessageContaining("2000");
    }
}
