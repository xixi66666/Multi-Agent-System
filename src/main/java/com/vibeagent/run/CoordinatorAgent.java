package com.vibeagent.run;

import com.vibeagent.agent.AgentContext;
import com.vibeagent.agent.AgentResult;
import com.vibeagent.agent.AgentRole;
import com.vibeagent.agent.SpecializedAgent;
import com.vibeagent.event.RunEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class CoordinatorAgent {

    private final Map<AgentRole, SpecializedAgent> agents;
    private final RunStore runStore;
    private final ExecutorService orchestrationExecutor;
    private final ExecutorService agentExecutor;
    private final RunEventService runEventService;
    private final AutonomousRunEngine autonomousRunEngine;

    public CoordinatorAgent(
            List<SpecializedAgent> agents,
            RunStore runStore,
            @Qualifier("orchestrationExecutor") ExecutorService orchestrationExecutor,
            @Qualifier("agentExecutor") ExecutorService agentExecutor) {
        this(agents, runStore, orchestrationExecutor, agentExecutor, null, null);
    }

    public CoordinatorAgent(
            List<SpecializedAgent> agents,
            RunStore runStore,
            @Qualifier("orchestrationExecutor") ExecutorService orchestrationExecutor,
            @Qualifier("agentExecutor") ExecutorService agentExecutor,
            RunEventService runEventService) {
        this(agents, runStore, orchestrationExecutor, agentExecutor, runEventService, null);
    }

    @Autowired
    public CoordinatorAgent(
            List<SpecializedAgent> agents,
            RunStore runStore,
            @Qualifier("orchestrationExecutor") ExecutorService orchestrationExecutor,
            @Qualifier("agentExecutor") ExecutorService agentExecutor,
            RunEventService runEventService,
            AutonomousRunEngine autonomousRunEngine) {
        this.agents = indexAgents(agents);
        this.runStore = runStore;
        this.orchestrationExecutor = orchestrationExecutor;
        this.agentExecutor = agentExecutor;
        this.runEventService = runEventService;
        this.autonomousRunEngine = autonomousRunEngine;
    }

    public RunSnapshot start(String requirement, String workspace) {
        return start(requirement, workspace, null);
    }

    public RunSnapshot start(String requirement, String workspace, UUID projectId) {
        CodingRun run = runStore.create(requirement, workspace, projectId);
        publish(run, "run.created", Map.of("status", run.snapshot().status().name()));
        Runnable execution = autonomousRunEngine == null
                ? () -> execute(run)
                : () -> autonomousRunEngine.execute(run);
        CompletableFuture.runAsync(execution, orchestrationExecutor);
        return run.snapshot();
    }

    public RunSnapshot get(UUID id) {
        return runStore.find(id)
                .orElseThrow(() -> new RunNotFoundException(id))
                .snapshot();
    }

    public List<RunSnapshot> list() {
        return runStore.findAll();
    }

    private void execute(CodingRun run) {
        try {
            transition(run, RunStatus.PLANNING);
            run.transitionTo(RunStatus.IMPLEMENTING);
            runStore.save(run);
            AgentContext implementationContext = contextFor(run, Map.of());

            CompletableFuture<AgentResult> frontend = executeAsync(AgentRole.FRONTEND, implementationContext);
            CompletableFuture<AgentResult> backend = executeAsync(AgentRole.BACKEND, implementationContext);
            CompletableFuture.allOf(frontend, backend).join();

            run.addResult(frontend.join());
            run.addResult(backend.join());
            runStore.save(run);
            publishResult(run, frontend.join());
            publishResult(run, backend.join());

            transition(run, RunStatus.TESTING);
            AgentResult testResult = agent(AgentRole.TEST).execute(contextFor(run, run.results()));
            run.addResult(testResult);
            runStore.save(run);
            publishResult(run, testResult);

            run.complete("Frontend, backend, and test agents completed successfully.");
            runStore.save(run);
            publish(run, "run.completed", Map.of("summary", run.snapshot().summary()));
        } catch (RuntimeException exception) {
            run.fail(exception);
            runStore.save(run);
            publish(run, "run.failed", Map.of("failure", run.snapshot().failure()));
        }
    }

    private void transition(CodingRun run, RunStatus status) {
        run.transitionTo(status);
        runStore.save(run);
        publish(run, "run.status.changed", Map.of("status", status.name()));
    }

    private void publishResult(CodingRun run, AgentResult result) {
        publish(run, "agent.completed", Map.of(
                "role", result.role().name(),
                "successful", result.successful(),
                "summary", result.summary()));
    }

    private void publish(CodingRun run, String type, Object payload) {
        if (runEventService != null) {
            runEventService.publish(run.id(), type, payload);
        }
    }

    private CompletableFuture<AgentResult> executeAsync(AgentRole role, AgentContext context) {
        return CompletableFuture.supplyAsync(() -> agent(role).execute(context), agentExecutor);
    }

    private AgentContext contextFor(CodingRun run, Map<AgentRole, AgentResult> priorResults) {
        return new AgentContext(run.id(), run.requirement(), run.workspace(), priorResults);
    }

    private SpecializedAgent agent(AgentRole role) {
        SpecializedAgent agent = agents.get(role);
        if (agent == null) {
            throw new IllegalStateException("Missing agent for role: " + role);
        }
        return agent;
    }

    private static Map<AgentRole, SpecializedAgent> indexAgents(List<SpecializedAgent> agents) {
        Map<AgentRole, SpecializedAgent> indexed = new EnumMap<>(AgentRole.class);
        for (SpecializedAgent agent : agents) {
            if (indexed.put(agent.role(), agent) != null) {
                throw new IllegalArgumentException("Duplicate agent for role: " + agent.role());
            }
        }
        return Map.copyOf(indexed);
    }
}
