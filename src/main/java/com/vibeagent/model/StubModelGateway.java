package com.vibeagent.model;

import com.vibeagent.event.RunEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "vibe.models", name = "enabled", havingValue = "false", matchIfMissing = true)
public class StubModelGateway implements ModelGateway {

    private final ModelUsageStore modelUsageStore;
    private final RunEventService runEventService;

    public StubModelGateway(ModelUsageStore modelUsageStore, RunEventService runEventService) {
        this.modelUsageStore = modelUsageStore;
        this.runEventService = runEventService;
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        String content = "Stub " + request.role().name().toLowerCase() + " response for run " + request.runId();
        long inputTokens = Math.max(1, (request.systemInstruction().length() + request.prompt().length()) / 4L);
        long outputTokens = Math.max(1, content.length() / 4L);
        ModelResponse response = new ModelResponse(
                content,
                "stub",
                "deterministic-local",
                "stop",
                inputTokens,
                outputTokens,
                0,
                0,
                inputTokens + outputTokens,
                BigDecimal.ZERO,
                true,
                0);
        ModelUsage usage = new ModelUsage(
                UUID.randomUUID(),
                request.runId(),
                request.taskId(),
                request.role(),
                response.provider(),
                response.model(),
                response.finishReason(),
                response.inputTokens(),
                response.outputTokens(),
                response.reasoningTokens(),
                response.cachedInputTokens(),
                response.totalTokens(),
                response.estimatedCost(),
                true,
                response.latencyMillis(),
                "SUCCESS",
                null,
                Instant.now());
        modelUsageStore.record(usage);
        runEventService.publish(request.runId(), "model.usage", Map.of(
                "role", request.role().name(),
                "provider", response.provider(),
                "model", response.model(),
                "totalTokens", response.totalTokens(),
                "estimated", true));
        return response;
    }
}
