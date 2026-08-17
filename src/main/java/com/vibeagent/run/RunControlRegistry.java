package com.vibeagent.run;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RunControlRegistry {

    private final ConcurrentMap<UUID, Control> controls = new ConcurrentHashMap<>();

    public void checkpoint(UUID runId) {
        Control control = controls.computeIfAbsent(runId, ignored -> new Control());
        synchronized (control) {
            while (control.paused && !control.cancelled) {
                try {
                    control.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RunCancelledException();
                }
            }
            if (control.cancelled) {
                throw new RunCancelledException();
            }
        }
    }

    void pause(UUID runId, RunStatus previousStatus) {
        Control control = controls.computeIfAbsent(runId, ignored -> new Control());
        synchronized (control) {
            control.previousStatus = previousStatus;
            control.paused = true;
        }
    }

    RunStatus resume(UUID runId) {
        Control control = controls.computeIfAbsent(runId, ignored -> new Control());
        synchronized (control) {
            control.paused = false;
            control.notifyAll();
            return control.previousStatus == null ? RunStatus.PLANNING : control.previousStatus;
        }
    }

    void cancel(UUID runId) {
        Control control = controls.computeIfAbsent(runId, ignored -> new Control());
        synchronized (control) {
            control.cancelled = true;
            control.paused = false;
            control.notifyAll();
        }
    }

    private static final class Control {
        private boolean paused;
        private boolean cancelled;
        private RunStatus previousStatus;
    }
}
