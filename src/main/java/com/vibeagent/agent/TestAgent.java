package com.vibeagent.agent;

import com.vibeagent.model.AgentModel;
import org.springframework.stereotype.Component;

@Component
public class TestAgent implements SpecializedAgent {

    private static final String INSTRUCTION = "Own acceptance criteria, startup instructions, and test execution.";

    private final AgentModel agentModel;

    public TestAgent(AgentModel agentModel) {
        this.agentModel = agentModel;
    }

    @Override
    public AgentRole role() {
        return AgentRole.TEST;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        return AgentResult.success(role(), agentModel.execute(role(), INSTRUCTION, context));
    }
}
