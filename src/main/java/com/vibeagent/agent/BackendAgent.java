package com.vibeagent.agent;

import com.vibeagent.model.AgentModel;
import org.springframework.stereotype.Component;

@Component
public class BackendAgent implements SpecializedAgent {

    private static final String INSTRUCTION = "Own data models, API contracts, and backend code.";

    private final AgentModel agentModel;

    public BackendAgent(AgentModel agentModel) {
        this.agentModel = agentModel;
    }

    @Override
    public AgentRole role() {
        return AgentRole.BACKEND;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        return AgentResult.success(role(), agentModel.execute(role(), INSTRUCTION, context));
    }
}
