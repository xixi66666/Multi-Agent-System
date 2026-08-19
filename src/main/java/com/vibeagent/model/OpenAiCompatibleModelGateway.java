package com.vibeagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeagent.agent.AgentRole;
import com.vibeagent.event.RunEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "vibe.models", name = "enabled", havingValue = "true")
public class OpenAiCompatibleModelGateway implements ModelGateway {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final AgentModelsProperties properties;
    private final ObjectMapper objectMapper;
    private final ModelUsageStore modelUsageStore;
    private final RunEventService runEventService;
    private final HttpClient httpClient;

    public OpenAiCompatibleModelGateway(
            AgentModelsProperties properties,
            ObjectMapper objectMapper,
            ModelUsageStore modelUsageStore,
            RunEventService runEventService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.modelUsageStore = modelUsageStore;
        this.runEventService = runEventService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        AgentModelsProperties.Route route = routeFor(request);
        List<String> providers = new ArrayList<>();
        addDistinct(providers, route.getPrimary());
        addDistinct(providers, route.getFallback());
        if (providers.isEmpty()) {
            throw new IllegalStateException("No model provider route configured for role " + request.role());
        }

        RuntimeException lastFailure = null;
        for (String providerName : providers) {
            try {
                return invoke(providerName, request);
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw new IllegalStateException("All configured model providers failed for role " + request.role(), lastFailure);
    }

    private ModelResponse invoke(String providerName, ModelRequest request) {
        AgentModelsProperties.Provider provider = requireProvider(providerName);
        validateProvider(providerName, provider);
        long startedNanos = System.nanoTime();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri(provider.getBaseUrl()))
                    .timeout(provider.getTimeout())
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(provider, request)))
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                throw new IllegalStateException("Provider " + providerName + " returned HTTP " + httpResponse.statusCode());
            }
            long latencyMillis = elapsedMillis(startedNanos);
            ModelResponse response = parseResponse(providerName, provider, request, httpResponse.body(), latencyMillis);
            recordSuccess(request, response);
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(providerName, provider, request, startedNanos, "INTERRUPTED");
            throw new IllegalStateException("Model request interrupted", exception);
        } catch (java.io.IOException exception) {
            recordFailure(providerName, provider, request, startedNanos, "IO_ERROR");
            throw new IllegalStateException("Model provider request failed", exception);
        } catch (RuntimeException exception) {
            recordFailure(providerName, provider, request, startedNanos, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    String requestBody(AgentModelsProperties.Provider provider, ModelRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.getModel());
        body.put("stream", false);
        if (provider.isStructuredOutput() && requiresStructuredOutput(request.role())) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemInstruction()),
                Map.of("role", "user", "content", request.prompt())));
        try {
            return objectMapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Model request could not be serialized", exception);
        }
    }

    ModelResponse parseResponse(
            String providerName,
            AgentModelsProperties.Provider provider,
            ModelRequest request,
            String responseBody,
            long latencyMillis) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("Provider response did not contain a completion choice");
            }
            String content = choices.get(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Provider response did not contain message content");
            }

            JsonNode usage = root.path("usage");
            boolean estimated = usage.isMissingNode() || usage.isEmpty();
            long inputTokens = estimated
                    ? estimateTokens(request.systemInstruction() + request.prompt())
                    : usage.path("prompt_tokens").asLong(0);
            long outputTokens = estimated ? estimateTokens(content) : usage.path("completion_tokens").asLong(0);
            long reasoningTokens = usage.path("completion_tokens_details").path("reasoning_tokens").asLong(0);
            long cachedInputTokens = usage.path("prompt_tokens_details").path("cached_tokens").asLong(0);
            long totalTokens = estimated
                    ? inputTokens + outputTokens
                    : usage.path("total_tokens").asLong(inputTokens + outputTokens);
            BigDecimal cost = estimateCost(provider, inputTokens, outputTokens);
            return new ModelResponse(
                    content,
                    providerName,
                    root.path("model").asText(provider.getModel()),
                    choices.get(0).path("finish_reason").asText(null),
                    inputTokens,
                    outputTokens,
                    reasoningTokens,
                    cachedInputTokens,
                    totalTokens,
                    cost,
                    estimated,
                    latencyMillis);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Provider response was not valid JSON", exception);
        }
    }

    private void recordSuccess(ModelRequest request, ModelResponse response) {
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
                response.usageEstimated(),
                response.latencyMillis(),
                "SUCCESS",
                null,
                Instant.now());
        modelUsageStore.record(usage);
        runEventService.publish(request.runId(), "model.usage", usageEvent(usage));
    }

    private void recordFailure(
            String providerName,
            AgentModelsProperties.Provider provider,
            ModelRequest request,
            long startedNanos,
            String failureType) {
        ModelUsage usage = new ModelUsage(
                UUID.randomUUID(),
                request.runId(),
                request.taskId(),
                request.role(),
                providerName,
                provider.getModel() == null ? "unknown" : provider.getModel(),
                null,
                0,
                0,
                0,
                0,
                0,
                BigDecimal.ZERO,
                false,
                elapsedMillis(startedNanos),
                "FAILED",
                failureType,
                Instant.now());
        modelUsageStore.record(usage);
        runEventService.publish(request.runId(), "model.failed", usageEvent(usage));
    }

    private Map<String, Object> usageEvent(ModelUsage usage) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("role", usage.role().name());
        event.put("provider", usage.provider());
        event.put("model", usage.model());
        if (usage.finishReason() != null) {
            event.put("finishReason", usage.finishReason());
        }
        event.put("totalTokens", usage.totalTokens());
        event.put("estimatedCost", usage.estimatedCost());
        event.put("latencyMillis", usage.latencyMillis());
        event.put("status", usage.requestStatus());
        if (usage.failureType() != null) {
            event.put("failureType", usage.failureType());
        }
        return event;
    }

    private boolean requiresStructuredOutput(AgentRole role) {
        return role == AgentRole.PLANNER || role == AgentRole.REVIEWER;
    }

    private AgentModelsProperties.Route routeFor(ModelRequest request) {
        String roleKey = request.role().name().toLowerCase(Locale.ROOT);
        AgentModelsProperties.Route route = properties.getRoutes().get(roleKey);
        if (route == null) {
            route = properties.getRoutes().get("default");
        }
        if (route == null) {
            throw new IllegalStateException("No default model route configured");
        }
        return route;
    }

    private AgentModelsProperties.Provider requireProvider(String providerName) {
        AgentModelsProperties.Provider provider = properties.getProviders().get(providerName);
        if (provider == null) {
            throw new IllegalStateException("Unknown model provider: " + providerName);
        }
        return provider;
    }

    private void validateProvider(String providerName, AgentModelsProperties.Provider provider) {
        if (isBlankOrPlaceholder(provider.getBaseUrl())
                || isBlankOrPlaceholder(provider.getApiKey())
                || isBlankOrPlaceholder(provider.getModel())) {
            throw new IllegalStateException("Model provider " + providerName + " is not fully configured");
        }
    }

    private URI chatCompletionsUri(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URI uri = URI.create(normalized + "/chat/completions");
        String scheme = uri.getScheme();
        boolean loopback = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!"https".equalsIgnoreCase(scheme) && !(loopback && "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Model endpoint must use HTTPS unless it is a loopback address");
        }
        return uri;
    }

    private BigDecimal estimateCost(AgentModelsProperties.Provider provider, long inputTokens, long outputTokens) {
        BigDecimal input = provider.getInputCostPerMillion()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
        BigDecimal output = provider.getOutputCostPerMillion()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
        return input.add(output);
    }

    private static void addDistinct(List<String> providers, String provider) {
        if (provider != null && !provider.isBlank() && !providers.contains(provider)) {
            providers.add(provider);
        }
    }

    private static boolean isBlankOrPlaceholder(String value) {
        return value == null || value.isBlank() || value.startsWith("YOUR_");
    }

    private static long estimateTokens(String value) {
        return Math.max(1L, (value.length() + 3L) / 4L);
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
