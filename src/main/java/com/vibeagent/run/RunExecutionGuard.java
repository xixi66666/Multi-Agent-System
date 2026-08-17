package com.vibeagent.run;

import com.vibeagent.model.ModelUsageStore;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RunExecutionGuard {

    private final RunControlRegistry controlRegistry;
    private final ModelUsageStore modelUsageStore;
    private final RuntimeProperties properties;

    public RunExecutionGuard(
            RunControlRegistry controlRegistry,
            ModelUsageStore modelUsageStore,
            RuntimeProperties properties) {
        this.controlRegistry = controlRegistry;
        this.modelUsageStore = modelUsageStore;
        this.properties = properties;
    }

    public void checkpoint(RunSnapshot run) {
        controlRegistry.checkpoint(run.id());
        if (properties.getMaxRuntime().isNegative() || properties.getMaxRuntime().isZero()) {
            throw new RunBudgetExceededException("Run time budget is disabled or invalid");
        }
        if (Instant.now().isAfter(run.createdAt().plus(properties.getMaxRuntime()))) {
            throw new RunBudgetExceededException("Run exceeded its maximum runtime");
        }
        long tokenLimit = properties.getMaxTotalTokens();
        if (tokenLimit > 0 && modelUsageStore.summary(run.id()).totalTokens() >= tokenLimit) {
            throw new RunBudgetExceededException("Run exceeded its total token budget");
        }
    }
}
