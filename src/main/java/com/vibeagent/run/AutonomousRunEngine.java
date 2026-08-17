package com.vibeagent.run;

import com.vibeagent.agent.AgentResult;
import com.vibeagent.agent.AgentRole;
import com.vibeagent.event.RunEventService;
import com.vibeagent.runtime.AgentTask;
import com.vibeagent.runtime.AgentTaskExecution;
import com.vibeagent.runtime.AgentTaskRuntime;
import com.vibeagent.runtime.AgentTaskStore;
import com.vibeagent.runtime.ExecutionPlan;
import com.vibeagent.runtime.PlannedTask;
import com.vibeagent.runtime.PlanningOutcome;
import com.vibeagent.runtime.PlanningService;
import com.vibeagent.runtime.ReviewExecution;
import com.vibeagent.runtime.ReviewService;
import com.vibeagent.workspace.TaskWorkspace;
import com.vibeagent.workspace.WorktreeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class AutonomousRunEngine {

    private final RunStore runStore;
    private final AgentTaskStore agentTaskStore;
    private final AgentTaskRuntime agentTaskRuntime;
    private final PlanningService planningService;
    private final RunEventService runEventService;
    private final ExecutorService agentExecutor;
    private final WorktreeService worktreeService;
    private final RunExecutionGuard executionGuard;
    private final RuntimeProperties runtimeProperties;
    private final ReviewService reviewService;

    public AutonomousRunEngine(
            RunStore runStore,
            AgentTaskStore agentTaskStore,
            AgentTaskRuntime agentTaskRuntime,
            PlanningService planningService,
            RunEventService runEventService,
            @Qualifier("agentExecutor") ExecutorService agentExecutor,
            WorktreeService worktreeService,
            RunExecutionGuard executionGuard,
            RuntimeProperties runtimeProperties,
            ReviewService reviewService) {
        this.runStore = runStore;
        this.agentTaskStore = agentTaskStore;
        this.agentTaskRuntime = agentTaskRuntime;
        this.planningService = planningService;
        this.runEventService = runEventService;
        this.agentExecutor = agentExecutor;
        this.worktreeService = worktreeService;
        this.executionGuard = executionGuard;
        this.runtimeProperties = runtimeProperties;
        this.reviewService = reviewService;
    }

    public void execute(CodingRun run) {
        List<AgentTaskExecution> executions = new ArrayList<>();
        try {
            TaskWorkspace integrationWorkspace = prepareIntegrationWorkspace(run);
            transition(run, RunStatus.PLANNING);
            PlanningOutcome planning = planningService.plan(run.snapshot());
            executions.add(planning.execution());
            addResult(run, planning.execution());

            ExecutionPlan plan = planning.plan();
            String sharedContext = planContext(plan);
            if (plan.requiresResearch()) {
                executionGuard.checkpoint(run.snapshot());
                AgentTaskExecution research = executeTask(
                        run,
                        planning.execution().task().id(),
                        AgentRole.RESEARCHER,
                        "documentation",
                        "Research required implementation details",
                        "Find repository and external documentation needed to implement this plan.",
                        sharedContext);
                executions.add(research);
                addResult(run, research);
                sharedContext = appendContext(sharedContext, research);
            }
            if (plan.requiresArchitecture()) {
                executionGuard.checkpoint(run.snapshot());
                AgentTaskExecution architecture = executeTask(
                        run,
                        planning.execution().task().id(),
                        AgentRole.ARCHITECT,
                        "system-design",
                        "Define implementation architecture",
                        "Define module boundaries and contracts for the approved plan.",
                        sharedContext);
                executions.add(architecture);
                addResult(run, architecture);
                sharedContext = appendContext(sharedContext, architecture);
            }

            transition(run, RunStatus.IMPLEMENTING);
            String implementationContext = sharedContext;
            List<TaskWorkspace> implementationWorkspaces = new ArrayList<>();
            List<CompletableFuture<AgentTaskExecution>> implementationFutures = new ArrayList<>();
            for (PlannedTask plannedTask : plan.implementationTasks()) {
                AgentTask task = agentTaskStore.create(
                        run.id(),
                        planning.execution().task().id(),
                        AgentRole.IMPLEMENTER,
                        plannedTask.specialty(),
                        plannedTask.title(),
                        plannedTask.instructions(),
                        3);
                TaskWorkspace taskWorkspace = integrationWorkspace == null
                        ? null
                        : worktreeService.prepareTask(run.projectId(), integrationWorkspace, task);
                implementationWorkspaces.add(taskWorkspace);
                RunSnapshot taskRun = taskWorkspace == null
                        ? run.snapshot()
                        : withWorkspace(run.snapshot(), taskWorkspace.path());
                implementationFutures.add(CompletableFuture.supplyAsync(
                        () -> agentTaskRuntime.execute(task, taskRun, implementationContext),
                        agentExecutor));
            }
            CompletableFuture.allOf(implementationFutures.toArray(CompletableFuture[]::new)).join();
            List<AgentTaskExecution> implementations = implementationFutures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            for (AgentTaskExecution implementation : implementations) {
                executions.add(implementation);
                addResult(run, implementation);
                sharedContext = appendContext(sharedContext, implementation);
            }
            if (integrationWorkspace != null) {
                executionGuard.checkpoint(run.snapshot());
                for (int index = 0; index < implementations.size(); index++) {
                    worktreeService.integrate(
                            integrationWorkspace,
                            implementationWorkspaces.get(index),
                            implementations.get(index).task());
                }
            }

            AgentTaskExecution integration = executeTask(
                    run,
                    planning.execution().task().id(),
                    AgentRole.INTEGRATOR,
                    "git-integration",
                    "Integrate implementation tasks",
                    "Combine the completed implementation work and report conflicts or integration risks.",
                    sharedContext);
            executions.add(integration);
            addResult(run, integration);
            sharedContext = appendContext(sharedContext, integration);

            transition(run, RunStatus.TESTING);
            AgentTaskExecution testing = executeTask(
                    run,
                    planning.execution().task().id(),
                    AgentRole.TESTER,
                    "verification",
                    "Verify acceptance criteria",
                    "Verify every acceptance criterion and report commands, evidence, failures, and residual risk.",
                    sharedContext);
            executions.add(testing);
            addResult(run, testing);
            sharedContext = appendContext(sharedContext, testing);
            if (integrationWorkspace != null) {
                worktreeService.commitIntegration(integrationWorkspace, "Add Agent verification changes");
                sharedContext = sharedContext + "\n\nFinal Git diff against "
                        + integrationWorkspace.baseRevision() + ":\n"
                        + worktreeService.finalDiff(integrationWorkspace);
            }

            transition(run, RunStatus.REVIEWING);
            ReviewExecution review = reviewService.review(
                    run.snapshot(), planning.execution().task().id(), sharedContext);
            executions.add(review.execution());
            addResult(run, review.execution());
            int repairRound = 0;
            while (!review.outcome().approved() && repairRound < Math.max(0, runtimeProperties.getMaxRepairRounds())) {
                repairRound++;
                runEventService.publish(run.id(), "repair.started", Map.of(
                        "round", repairRound,
                        "findings", review.outcome().findings()));

                transition(run, RunStatus.IMPLEMENTING);
                AgentTaskExecution repair = executeTask(
                        run,
                        planning.execution().task().id(),
                        AgentRole.IMPLEMENTER,
                        "review-repair",
                        "Resolve review findings (round " + repairRound + ")",
                        "Address only these review findings and preserve verified behavior: "
                                + String.join("; ", review.outcome().findings()),
                        sharedContext);
                executions.add(repair);
                addResult(run, repair);
                sharedContext = appendContext(sharedContext, repair);
                if (integrationWorkspace != null) {
                    worktreeService.commitIntegration(
                            integrationWorkspace, "Address review findings round " + repairRound);
                }

                transition(run, RunStatus.TESTING);
                AgentTaskExecution retest = executeTask(
                        run,
                        planning.execution().task().id(),
                        AgentRole.TESTER,
                        "repair-verification",
                        "Verify repaired change (round " + repairRound + ")",
                        "Re-run the relevant verification after review repairs and report concrete evidence.",
                        sharedContext);
                executions.add(retest);
                addResult(run, retest);
                sharedContext = appendContext(sharedContext, retest);
                if (integrationWorkspace != null) {
                    worktreeService.commitIntegration(
                            integrationWorkspace, "Add repair verification round " + repairRound);
                    sharedContext = appendFinalDiff(sharedContext, integrationWorkspace);
                }

                transition(run, RunStatus.REVIEWING);
                review = reviewService.review(
                        run.snapshot(), planning.execution().task().id(), sharedContext);
                executions.add(review.execution());
                addResult(run, review.execution());
            }
            if (!review.outcome().approved()) {
                String summary = "Review requires attention: " + String.join("; ", review.outcome().findings());
                needsAttention(run, summary, Map.of("findings", review.outcome().findings()));
                return;
            }

            boolean stubbed = executions.stream()
                    .anyMatch(execution -> "stub".equals(execution.modelResponse().provider()));
            String summary = stubbed
                    ? "Autonomous workflow completed in Stub mode; no real model or workspace tools were used."
                    : "Planning, implementation, integration, testing, and review agents completed successfully.";
            if (stubbed) {
                run.completeWithWarnings(summary);
            } else {
                run.complete(summary);
            }
            runStore.save(run);
            runEventService.publish(run.id(), "run.completed", Map.of(
                    "status", run.snapshot().status().name(),
                    "summary", summary));
        } catch (RunCancelledException exception) {
            if (run.snapshot().status() != RunStatus.CANCELLED) {
                run.transitionTo(RunStatus.CANCELLED);
                runStore.save(run);
            }
        } catch (RuntimeException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof RunBudgetExceededException) {
                needsAttention(run, cause.getMessage(), Map.of("reason", "BUDGET_EXCEEDED"));
                return;
            }
            run.fail(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            runStore.save(run);
            runEventService.publish(run.id(), "run.failed", Map.of(
                    "failure", run.snapshot().failure()));
        }
    }

    private AgentTaskExecution executeTask(
            CodingRun run,
            java.util.UUID parentTaskId,
            AgentRole role,
            String specialty,
            String title,
            String instructions,
            String sharedContext) {
        executionGuard.checkpoint(run.snapshot());
        AgentTask task = agentTaskStore.create(
                run.id(), parentTaskId, role, specialty, title, instructions, 3);
        return agentTaskRuntime.execute(task, run.snapshot(), sharedContext);
    }

    private void addResult(CodingRun run, AgentTaskExecution execution) {
        run.addResult(AgentResult.success(execution.task().role(), execution.task().resultSummary()));
        runStore.save(run);
    }

    private void transition(CodingRun run, RunStatus status) {
        executionGuard.checkpoint(run.snapshot());
        run.transitionTo(status);
        runStore.save(run);
        runEventService.publish(run.id(), "run.status.changed", Map.of("status", status.name()));
    }

    private TaskWorkspace prepareIntegrationWorkspace(CodingRun run) {
        if (run.projectId() == null) {
            return null;
        }
        TaskWorkspace workspace = worktreeService.prepareIntegration(run.id(), run.projectId());
        run.useWorkspace(workspace.path());
        runStore.save(run);
        runEventService.publish(run.id(), "workspace.prepared", Map.of(
                "workspaceId", workspace.id(),
                "type", workspace.type().name(),
                "path", workspace.path(),
                "branch", workspace.branchName()));
        return workspace;
    }

    private RunSnapshot withWorkspace(RunSnapshot snapshot, String workspace) {
        return new RunSnapshot(
                snapshot.id(),
                snapshot.projectId(),
                snapshot.requirement(),
                workspace,
                snapshot.status(),
                snapshot.results(),
                snapshot.summary(),
                snapshot.failure(),
                snapshot.createdAt(),
                snapshot.updatedAt());
    }

    private String planContext(ExecutionPlan plan) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("planSummary", plan.summary());
        context.put("acceptanceCriteria", plan.acceptanceCriteria());
        context.put("implementationTasks", plan.implementationTasks());
        return context.toString();
    }

    private String appendContext(String context, AgentTaskExecution execution) {
        return context + "\n\n" + execution.task().role() + " / " + execution.task().title()
                + ":\n" + execution.task().resultSummary();
    }

    private String appendFinalDiff(String context, TaskWorkspace integrationWorkspace) {
        return context + "\n\nFinal Git diff against "
                + integrationWorkspace.baseRevision() + ":\n"
                + worktreeService.finalDiff(integrationWorkspace);
    }

    private void needsAttention(CodingRun run, String summary, Map<String, Object> details) {
        run.needsAttention(summary);
        runStore.save(run);
        Map<String, Object> payload = new LinkedHashMap<>(details);
        payload.put("summary", summary);
        runEventService.publish(run.id(), "run.needs-attention", payload);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
