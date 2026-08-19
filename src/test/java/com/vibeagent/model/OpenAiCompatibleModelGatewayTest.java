package com.vibeagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeagent.agent.AgentRole;
import com.vibeagent.event.RunEventService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiCompatibleModelGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiCompatibleModelGateway gateway = new OpenAiCompatibleModelGateway(
            new AgentModelsProperties(),
            objectMapper,
            mock(ModelUsageStore.class),
            mock(RunEventService.class));

    @Test
    void requestsJsonObjectForStructuredRolesWhenProviderSupportsIt() throws Exception {
        AgentModelsProperties.Provider provider = provider();
        ModelRequest planner = request(AgentRole.PLANNER);
        ModelRequest reviewer = request(AgentRole.REVIEWER);
        ModelRequest implementer = request(AgentRole.IMPLEMENTER);

        JsonNode plannerBody = objectMapper.readTree(gateway.requestBody(provider, planner));
        JsonNode reviewerBody = objectMapper.readTree(gateway.requestBody(provider, reviewer));
        JsonNode implementerBody = objectMapper.readTree(gateway.requestBody(provider, implementer));

        assertThat(plannerBody.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(reviewerBody.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(implementerBody.has("response_format")).isFalse();
    }

    @Test
    void canDisableStructuredOutputForIncompatibleProvider() throws Exception {
        AgentModelsProperties.Provider provider = provider();
        provider.setStructuredOutput(false);

        JsonNode body = objectMapper.readTree(gateway.requestBody(provider, request(AgentRole.PLANNER)));

        assertThat(body.has("response_format")).isFalse();
    }

    @Test
    void capturesProviderFinishReason() {
        AgentModelsProperties.Provider provider = provider();
        String responseBody = """
                {
                  "model": "test-model",
                  "choices": [{
                    "finish_reason": "length",
                    "message": {"content": "{\\\"summary\\\":\\\"partial\\\"}"}
                  }],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                }
                """;

        ModelResponse response = gateway.parseResponse(
                "test-provider", provider, request(AgentRole.PLANNER), responseBody, 12);

        assertThat(response.finishReason()).isEqualTo("length");
    }

    private AgentModelsProperties.Provider provider() {
        AgentModelsProperties.Provider provider = new AgentModelsProperties.Provider();
        provider.setModel("test-model");
        return provider;
    }

    private ModelRequest request(AgentRole role) {
        return new ModelRequest(UUID.randomUUID(), UUID.randomUUID(), role, "system", "prompt");
    }
}
