package com.vibeagent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RunControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsRunAndExposesTaskEventAndUsageViews() throws Exception {
        String response = mockMvc.perform(post("/api/runs")
                        .contentType("application/json")
                        .content("""
                                {
                                  "requirement": "Add a diagnostic endpoint",
                                  "workspace": "D:/workspace"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String runId = objectMapper.readTree(response).path("id").asText();

        JsonNode run = awaitTerminalRun(runId);
        assertThat(run.path("status").asText()).isEqualTo("COMPLETED_WITH_WARNINGS");

        JsonNode tasks = readJson("/api/runs/" + runId + "/tasks");
        JsonNode events = readJson("/api/runs/" + runId + "/events");
        JsonNode usage = readJson("/api/runs/" + runId + "/usage");
        JsonNode messages = readJson("/api/runs/" + runId + "/messages");

        assertThat(tasks).hasSize(5);
        assertThat(events.size()).isGreaterThanOrEqualTo(20);
        assertThat(usage.path("calls").asInt()).isEqualTo(5);
        assertThat(messages).hasSize(5);
    }

    private JsonNode awaitTerminalRun(String runId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        JsonNode run;
        do {
            run = readJson("/api/runs/" + runId);
            String status = run.path("status").asText();
            if (status.equals("COMPLETED_WITH_WARNINGS") || status.equals("FAILED")) {
                return run;
            }
            Thread.sleep(10);
        } while (Instant.now().isBefore(deadline));
        return run;
    }

    private JsonNode readJson(String path) throws Exception {
        String response = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }
}
