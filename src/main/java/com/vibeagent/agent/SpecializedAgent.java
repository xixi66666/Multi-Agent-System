package com.vibeagent.agent;

public interface SpecializedAgent {

    AgentRole role();

    AgentResult execute(AgentContext context);
}
