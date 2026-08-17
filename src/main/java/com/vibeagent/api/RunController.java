package com.vibeagent.api;

import com.vibeagent.collaboration.AgentMessage;
import com.vibeagent.collaboration.AgentMessageStore;
import com.vibeagent.event.RunEvent;
import com.vibeagent.event.RunEventService;
import com.vibeagent.model.ModelUsageStore;
import com.vibeagent.model.ModelUsageSummary;
import com.vibeagent.project.Project;
import com.vibeagent.project.ProjectService;
import com.vibeagent.run.CoordinatorAgent;
import com.vibeagent.run.RunSnapshot;
import com.vibeagent.run.RunControlService;
import com.vibeagent.runtime.AgentTask;
import com.vibeagent.runtime.AgentTaskStore;
import com.vibeagent.workspace.TaskWorkspace;
import com.vibeagent.workspace.TaskWorkspaceStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final CoordinatorAgent coordinatorAgent;
    private final RunEventService runEventService;
    private final ModelUsageStore modelUsageStore;
    private final AgentTaskStore agentTaskStore;
    private final ProjectService projectService;
    private final TaskWorkspaceStore taskWorkspaceStore;
    private final RunControlService runControlService;
    private final AgentMessageStore agentMessageStore;

    public RunController(
            CoordinatorAgent coordinatorAgent,
            RunEventService runEventService,
            ModelUsageStore modelUsageStore,
            AgentTaskStore agentTaskStore,
            ProjectService projectService,
            TaskWorkspaceStore taskWorkspaceStore,
            RunControlService runControlService,
            AgentMessageStore agentMessageStore) {
        this.coordinatorAgent = coordinatorAgent;
        this.runEventService = runEventService;
        this.modelUsageStore = modelUsageStore;
        this.agentTaskStore = agentTaskStore;
        this.projectService = projectService;
        this.taskWorkspaceStore = taskWorkspaceStore;
        this.runControlService = runControlService;
        this.agentMessageStore = agentMessageStore;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunSnapshot create(@Valid @RequestBody CreateRunRequest request) {
        if (request.projectId() == null) {
            return coordinatorAgent.start(request.requirement(), request.workspace());
        }
        Project project = projectService.get(request.projectId());
        return coordinatorAgent.start(request.requirement(), project.rootPath(), project.id());
    }

    @GetMapping
    public List<RunSnapshot> list() {
        return coordinatorAgent.list();
    }

    @GetMapping("/{id}")
    public RunSnapshot get(@PathVariable UUID id) {
        return coordinatorAgent.get(id);
    }

    @GetMapping("/{id}/events")
    public List<RunEvent> events(@PathVariable UUID id, @RequestParam(defaultValue = "0") long after) {
        coordinatorAgent.get(id);
        return runEventService.eventsAfter(id, after);
    }

    @GetMapping(value = "/{id}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id, @RequestParam(defaultValue = "0") long after) {
        coordinatorAgent.get(id);
        return runEventService.subscribe(id, after);
    }

    @GetMapping("/{id}/usage")
    public ModelUsageSummary usage(@PathVariable UUID id) {
        coordinatorAgent.get(id);
        return modelUsageStore.summary(id);
    }

    @GetMapping("/{id}/tasks")
    public List<AgentTask> tasks(@PathVariable UUID id) {
        coordinatorAgent.get(id);
        return agentTaskStore.findByRun(id);
    }

    @GetMapping("/{id}/workspaces")
    public List<TaskWorkspace> workspaces(@PathVariable UUID id) {
        coordinatorAgent.get(id);
        return taskWorkspaceStore.findByRun(id);
    }

    @GetMapping("/{id}/messages")
    public List<AgentMessage> messages(@PathVariable UUID id) {
        coordinatorAgent.get(id);
        return agentMessageStore.findByRun(id);
    }

    @PostMapping("/{id}/pause")
    public RunSnapshot pause(@PathVariable UUID id) {
        return runControlService.pause(id);
    }

    @PostMapping("/{id}/resume")
    public RunSnapshot resume(@PathVariable UUID id) {
        return runControlService.resume(id);
    }

    @PostMapping("/{id}/cancel")
    public RunSnapshot cancel(@PathVariable UUID id) {
        return runControlService.cancel(id);
    }
}
