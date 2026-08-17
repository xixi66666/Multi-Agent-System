package com.vibeagent;

import com.vibeagent.model.AgentModelsProperties;
import com.vibeagent.project.WorkspaceProperties;
import com.vibeagent.run.RuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgentModelsProperties.class, WorkspaceProperties.class, RuntimeProperties.class})
public class VibeAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(VibeAgentApplication.class, args);
    }
}
