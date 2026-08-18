package com.vibeagent.run;

import com.vibeagent.workspace.WorktreeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RunDeletionService {

    private final RunStore runStore;
    private final WorktreeService worktreeService;

    public RunDeletionService(RunStore runStore, WorktreeService worktreeService) {
        this.runStore = runStore;
        this.worktreeService = worktreeService;
    }

    @Transactional
    public void delete(UUID runId) {
        CodingRun run = runStore.find(runId)
                .orElseThrow(() -> new RunNotFoundException(runId));
        RunSnapshot snapshot = run.snapshot();
        requireDeletable(snapshot.status());
        deleteRunState(snapshot);
        runStore.delete(runId);
    }

    @Transactional
    public int deleteByProject(UUID projectId) {
        List<RunSnapshot> deletable = runStore.findAll().stream()
                .filter(snapshot -> projectId.equals(snapshot.projectId()) && isDeletable(snapshot.status()))
                .toList();
        for (RunSnapshot snapshot : deletable) {
            deleteRunState(snapshot);
            runStore.delete(snapshot.id());
        }
        return deletable.size();
    }

    private void deleteRunState(RunSnapshot snapshot) {
        if (snapshot.projectId() != null) {
            worktreeService.removeRun(snapshot.projectId(), snapshot.id());
        }
    }

    private void requireDeletable(RunStatus status) {
        if (!isDeletable(status)) {
            throw new IllegalStateException("Only terminal runs can be deleted");
        }
    }

    private boolean isDeletable(RunStatus status) {
        return status.isTerminal() || status == RunStatus.NEEDS_ATTENTION;
    }
}
