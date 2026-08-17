package com.vibeagent.agent;

import com.vibeagent.model.AgentModel;
import org.springframework.stereotype.Component;

@Component
public class FrontendAgent implements SpecializedAgent {

    private static final String INSTRUCTION = "Own page specifications, API contracts, and frontend code.";

    private final AgentModel agentModel;

    public FrontendAgent(AgentModel agentModel) {
        this.agentModel = agentModel;
    }

    @Override
    public AgentRole role() {
        return AgentRole.FRONTEND;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        return AgentResult.success(role(), agentModel.execute(role(), INSTRUCTION, context));
    }
}
