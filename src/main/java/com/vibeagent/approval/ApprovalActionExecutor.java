package com.vibeagent.approval;

public interface ApprovalActionExecutor {

    String actionType();

    void execute(Approval approval);
}
