package com.vibeagent.model;

import com.vibeagent.agent.AgentContext;
import com.vibeagent.agent.AgentRole;
import org.springframework.stereotype.Component;

@Component
public class StubAgentModel implements AgentModel {

    @Override
    public String execute(AgentRole role, String systemInstruction, AgentContext context) {
        return switch (role) {
            case PLANNER -> "Execution plan prepared for workspace: " + context.workspace();
            case ARCHITECT -> "Architecture decision prepared for the requested change.";
            case IMPLEMENTER -> "Implementation change prepared for workspace: " + context.workspace();
            case TESTER -> "Verification report prepared from implementation results.";
            case REVIEWER -> "Final diff review completed.";
            case INTEGRATOR -> "Implementation branches integrated locally.";
            case RESEARCHER -> "Relevant project and documentation findings prepared.";
            case FRONTEND -> "Frontend plan prepared for workspace: " + context.workspace();
            case BACKEND -> "Backend plan prepared for workspace: " + context.workspace();
            case TEST -> "Acceptance plan prepared from frontend and backend results.";
        };
    }
}
