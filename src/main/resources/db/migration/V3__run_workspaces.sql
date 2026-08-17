CREATE TABLE run_workspaces (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    task_id BINARY(16) NULL,
    workspace_type VARCHAR(32) NOT NULL,
    workspace_path VARCHAR(2048) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_run_workspaces_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_run_workspaces_task
        FOREIGN KEY (task_id) REFERENCES agent_tasks (id)
);
CREATE INDEX idx_run_workspaces_run ON run_workspaces (run_id, workspace_type);
CREATE INDEX idx_run_workspaces_task ON run_workspaces (task_id);
