package com.vibeagent.runtime;

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
        this.objectMapper = objectMapper;
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
                        + "Use between one and four implementation tasks and keep their scopes non-overlapping.",
                3);
        AgentTaskExecution execution = agentTaskRuntime.execute(
                task, run, repositoryContextService.build(java.nio.file.Path.of(run.workspace())));
        if ("stub".equals(execution.modelResponse().provider())) {
            return new PlanningOutcome(defaultPlan(run), execution);
        }
        return new PlanningOutcome(parsePlan(execution.modelResponse().content()), execution);
    }

    private ExecutionPlan parsePlan(String content) {
        String json = extractJsonObject(content);
        try {
            return validate(objectMapper.readValue(json, ExecutionPlan.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Planner did not return a valid execution plan", exception);
        }
    }

    private ExecutionPlan validate(ExecutionPlan plan) {
        List<PlannedTask> validTasks = new ArrayList<>();
        for (PlannedTask task : plan.implementationTasks()) {
            if (task != null
                    && task.title() != null && !task.title().isBlank()
                    && task.instructions() != null && !task.instructions().isBlank()) {
                validTasks.add(task);
            }
            if (validTasks.size() == MAX_IMPLEMENTATION_TASKS) {
                break;
            }
        }
        if (validTasks.isEmpty()) {
            throw new IllegalStateException("Planner returned no executable implementation tasks");
        }
        return new ExecutionPlan(
                plan.summary(),
                plan.requiresArchitecture(),
                plan.requiresResearch(),
                validTasks,
                plan.acceptanceCriteria());
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
