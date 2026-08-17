package com.vibeagent.run;

import com.vibeagent.event.RunEventService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class RunControlService {

    private final RunStore runStore;
    private final RunControlRegistry controlRegistry;
    private final RunEventService runEventService;

    public RunControlService(
            RunStore runStore,
            RunControlRegistry controlRegistry,
            RunEventService runEventService) {
        this.runStore = runStore;
        this.controlRegistry = controlRegistry;
        this.runEventService = runEventService;
    }

    public RunSnapshot pause(UUID runId) {
        CodingRun run = requireRun(runId);
        RunSnapshot snapshot = run.snapshot();
        if (snapshot.status().isTerminal()) {
            throw new IllegalStateException("Terminal run cannot be paused");
        }
        controlRegistry.pause(runId, snapshot.status());
        run.transitionTo(RunStatus.PAUSED);
        runStore.save(run);
        runEventService.publish(runId, "run.paused", Map.of("previousStatus", snapshot.status().name()));
        return run.snapshot();
    }

    public RunSnapshot resume(UUID runId) {
        CodingRun run = requireRun(runId);
        if (run.snapshot().status() != RunStatus.PAUSED) {
            throw new IllegalStateException("Only a paused run can be resumed");
        }
        RunStatus resumedStatus = controlRegistry.resume(runId);
        run.transitionTo(resumedStatus);
        runStore.save(run);
        runEventService.publish(runId, "run.resumed", Map.of("status", resumedStatus.name()));
        return run.snapshot();
    }

    public RunSnapshot cancel(UUID runId) {
        CodingRun run = requireRun(runId);
        if (run.snapshot().status().isTerminal()) {
            return run.snapshot();
        }
        controlRegistry.cancel(runId);
        run.transitionTo(RunStatus.CANCELLED);
        runStore.save(run);
        runEventService.publish(runId, "run.cancelled", Map.of("status", RunStatus.CANCELLED.name()));
        return run.snapshot();
    }

    private CodingRun requireRun(UUID runId) {
        return runStore.find(runId).orElseThrow(() -> new RunNotFoundException(runId));
    }
}
