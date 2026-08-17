package com.vibeagent.model;

import com.vibeagent.agent.AgentContext;
import com.vibeagent.agent.AgentRole;

public interface AgentModel {

    String execute(AgentRole role, String systemInstruction, AgentContext context);
}
