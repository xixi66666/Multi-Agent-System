package com.vibeagent.run;

import com.vibeagent.event.RunEventService;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class RunRecoveryService {

    private static final Set<RunStatus> INTERRUPTED_STATUSES = Set.of(
            RunStatus.CREATED,
            RunStatus.PLANNING,
            RunStatus.IMPLEMENTING,
            RunStatus.TESTING,
            RunStatus.REVIEWING,
            RunStatus.PAUSED);

    private final RunStore runStore;
    private final RunEventService runEventService;

    public RunRecoveryService(RunStore runStore, RunEventService runEventService) {
        this.runStore = runStore;
        this.runEventService = runEventService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        for (RunSnapshot snapshot : runStore.findAll()) {
            if (!INTERRUPTED_STATUSES.contains(snapshot.status())) {
                continue;
            }
            CodingRun run = runStore.find(snapshot.id()).orElseThrow();
            String summary = "Execution was interrupted by a runtime restart; worktrees and audit evidence were preserved.";
            run.needsAttention(summary);
            runStore.save(run);
            runEventService.publish(run.id(), "run.recovered.needs-attention", Map.of(
                    "previousStatus", snapshot.status().name(),
                    "summary", summary));
        }
    }
}
