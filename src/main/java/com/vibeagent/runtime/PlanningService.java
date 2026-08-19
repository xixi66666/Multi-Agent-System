package com.vibeagent.runtime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeagent.agent.AgentRole;
import com.vibeagent.run.RunSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlanningService {

    private static final int MAX_IMPLEMENTATION_TASKS = 4;
    private static final int MAX_SUMMARY_CHARS = 600;
    private static final int MAX_TITLE_CHARS = 160;
    private static final int MAX_SPECIALTY_CHARS = 80;
    private static final int MAX_INSTRUCTIONS_CHARS = 2_000;
    private static final int MAX_ACCEPTANCE_CRITERIA = 8;
    private static final int MAX_ACCEPTANCE_CRITERION_CHARS = 400;

    private final AgentTaskStore agentTaskStore;
    private final AgentTaskRuntime agentTaskRuntime;
    private final ObjectMapper objectMapper;
    private final RepositoryContextService repositoryContextService;

    public PlanningService(
            AgentTaskStore agentTaskStore,
            AgentTaskRuntime agentTaskRuntime,
            ObjectMapper objectMapper,
            RepositoryContextService repositoryContextService) {
        this.agentTaskStore = agentTaskStore;
        this.agentTaskRuntime = agentTaskRuntime;
        this.objectMapper = objectMapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.repositoryContextService = repositoryContextService;
    }

    public PlanningOutcome plan(RunSnapshot run) {
        AgentTask task = agentTaskStore.create(
                run.id(),
                null,
                AgentRole.PLANNER,
                "requirements",
                "Analyze requirement and build execution plan",
                "Return JSON only with this shape: {\"summary\": string, \"requiresArchitecture\": boolean, "
                        + "\"requiresResearch\": boolean, \"implementationTasks\": [{\"title\": string, "
                        + "\"specialty\": string, \"instructions\": string}], \"acceptanceCriteria\": [string]}. "
                        + "Use between one and four implementation tasks. They run concurrently in isolated worktrees, "
                        + "so every task must be independent and their file scopes must not overlap. If work has an "
                        + "ordering dependency, combine it into one self-contained task. Do not create a standalone "
                        + "test or verification implementation task because a dedicated TESTER runs after integration. "
                        + "Each implementation task may include tests owned by its own change. Keep summary within 600 "
                        + "characters, each title within 160, specialty within 80, instructions within 2000, and return "
                        + "between one and eight acceptance criteria of at most 400 characters each.",
                3);
        AgentTaskExecution execution = agentTaskRuntime.execute(
                task,
                run,
                repositoryContextService.build(java.nio.file.Path.of(run.workspace())),
                content -> {
                    parsePlan(content);
                    return content;
                });
        if ("stub".equals(execution.modelResponse().provider())) {
            return new PlanningOutcome(defaultPlan(run), execution);
        }
        return new PlanningOutcome(parsePlan(execution.modelResponse().content()), execution);
    }

    ExecutionPlan parsePlan(String content) {
        String json = extractJsonObject(content);
        try {
            return validate(objectMapper.readValue(json, ExecutionPlan.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Planner returned duplicate or invalid JSON fields: " + concise(exception.getOriginalMessage()),
                    exception);
        }
    }

    private ExecutionPlan validate(ExecutionPlan plan) {
        if (plan.summary() == null || plan.summary().isBlank() || plan.summary().length() > MAX_SUMMARY_CHARS) {
            throw new IllegalStateException("Planner summary must contain 1 to " + MAX_SUMMARY_CHARS + " characters");
        }
        if (plan.implementationTasks().isEmpty() || plan.implementationTasks().size() > MAX_IMPLEMENTATION_TASKS) {
            throw new IllegalStateException(
                    "Planner must return between 1 and " + MAX_IMPLEMENTATION_TASKS + " implementation tasks");
        }
        List<PlannedTask> validTasks = new ArrayList<>(plan.implementationTasks().size());
        for (PlannedTask task : plan.implementationTasks()) {
            if (task == null) {
                throw new IllegalStateException("Planner implementation task cannot be null");
            }
            requireText("title", task.title(), MAX_TITLE_CHARS);
            requireText("specialty", task.specialty(), MAX_SPECIALTY_CHARS);
            requireText("instructions", task.instructions(), MAX_INSTRUCTIONS_CHARS);
            validTasks.add(task);
        }
        if (plan.acceptanceCriteria().isEmpty()
                || plan.acceptanceCriteria().size() > MAX_ACCEPTANCE_CRITERIA) {
            throw new IllegalStateException(
                    "Planner must return between 1 and " + MAX_ACCEPTANCE_CRITERIA + " acceptance criteria");
        }
        for (String criterion : plan.acceptanceCriteria()) {
            requireText("acceptance criterion", criterion, MAX_ACCEPTANCE_CRITERION_CHARS);
        }
        return new ExecutionPlan(
                plan.summary(),
                plan.requiresArchitecture(),
                plan.requiresResearch(),
                validTasks,
                plan.acceptanceCriteria());
    }

    private void requireText(String field, String value, int maxChars) {
        if (value == null || value.isBlank() || value.length() > maxChars) {
            throw new IllegalStateException(
                    "Planner " + field + " must contain 1 to " + maxChars + " characters");
        }
    }

    private String concise(String message) {
        if (message == null || message.isBlank()) {
            return "unknown JSON parsing error";
        }
        if (message.length() <= 500) {
            return message;
        }
        return message.substring(0, 500) + " [truncated]";
    }

    private ExecutionPlan defaultPlan(RunSnapshot run) {
        return new ExecutionPlan(
                "Prepare and verify the requested workspace change.",
                false,
                false,
                List.of(new PlannedTask(
                        "Implement requested change",
                        "general",
                        "Implement the requirement in the existing repository and preserve established conventions.")),
                List.of("The requested behavior is implemented.", "Relevant project checks pass."));
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Planner response did not contain a JSON object");
        }
        return content.substring(start, end + 1);
    }
}
