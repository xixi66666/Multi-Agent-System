package com.vibeagent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeagent.agent.AgentRole;
import com.vibeagent.run.RunSnapshot;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {

    private final AgentTaskStore agentTaskStore;
    private final AgentTaskRuntime agentTaskRuntime;
    private final ObjectMapper objectMapper;

    public ReviewService(
            AgentTaskStore agentTaskStore,
            AgentTaskRuntime agentTaskRuntime,
            ObjectMapper objectMapper) {
        this.agentTaskStore = agentTaskStore;
        this.agentTaskRuntime = agentTaskRuntime;
        this.objectMapper = objectMapper;
    }

    public ReviewExecution review(RunSnapshot run, UUID parentTaskId, String sharedContext) {
        AgentTask task = agentTaskStore.create(
                run.id(),
                parentTaskId,
                AgentRole.REVIEWER,
                "final-review",
                "Review final change",
                "Review the integrated change against the requirement, plan, test evidence, security boundaries, and final diff. "
                        + "Return JSON only: {\"approved\": boolean, \"summary\": string, \"findings\": [string]}.",
                3);
        AgentTaskExecution execution = agentTaskRuntime.execute(task, run, sharedContext);
        if ("stub".equals(execution.modelResponse().provider())) {
            return new ReviewExecution(
                    new ReviewOutcome(true, "Stub review completed without inspecting a real change.", List.of()),
                    execution);
        }
        ReviewOutcome outcome = parse(execution.modelResponse().content());
        if (outcome.summary() == null || outcome.summary().isBlank()) {
            throw new IllegalStateException("Reviewer returned no summary");
        }
        if (!outcome.approved() && outcome.findings().isEmpty()) {
            throw new IllegalStateException("Rejected review must include actionable findings");
        }
        return new ReviewExecution(outcome, execution);
    }

    private ReviewOutcome parse(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Reviewer response did not contain a JSON object");
        }
        try {
            return objectMapper.readValue(content.substring(start, end + 1), ReviewOutcome.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Reviewer did not return a valid structured verdict", exception);
        }
    }
}
