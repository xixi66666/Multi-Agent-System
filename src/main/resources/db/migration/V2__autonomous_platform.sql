CREATE TABLE projects (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    root_path VARCHAR(2048) NOT NULL,
    path_hash CHAR(64) NOT NULL,
    project_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (path_hash)
);

ALTER TABLE runs ADD COLUMN project_id BINARY(16) NULL;
ALTER TABLE runs ADD COLUMN max_runtime_seconds INT NOT NULL DEFAULT 3600;
ALTER TABLE runs ADD COLUMN max_repair_rounds SMALLINT NOT NULL DEFAULT 3;
ALTER TABLE runs ADD COLUMN max_replans SMALLINT NOT NULL DEFAULT 2;
ALTER TABLE runs ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE runs ADD CONSTRAINT fk_runs_project
    FOREIGN KEY (project_id) REFERENCES projects (id);

CREATE TABLE agent_tasks (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    parent_task_id BINARY(16) NULL,
    role VARCHAR(64) NOT NULL,
    specialty VARCHAR(128) NULL,
    title VARCHAR(255) NOT NULL,
    instructions TEXT NULL,
    status VARCHAR(64) NOT NULL,
    attempt SMALLINT NOT NULL DEFAULT 0,
    max_attempts SMALLINT NOT NULL DEFAULT 3,
    lease_owner VARCHAR(255) NULL,
    lease_until TIMESTAMP(6) NULL,
    result_summary TEXT NULL,
    failure TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_tasks_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_agent_tasks_parent
        FOREIGN KEY (parent_task_id) REFERENCES agent_tasks (id)
);
CREATE INDEX idx_agent_tasks_run_status ON agent_tasks (run_id, status);
CREATE INDEX idx_agent_tasks_lease ON agent_tasks (status, lease_until);

CREATE TABLE agent_messages (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    task_id BINARY(16) NULL,
    sender_role VARCHAR(64) NOT NULL,
    recipient_role VARCHAR(64) NULL,
    message_type VARCHAR(64) NOT NULL,
    schema_version VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_messages_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_agent_messages_task
        FOREIGN KEY (task_id) REFERENCES agent_tasks (id)
);
CREATE INDEX idx_agent_messages_run_created ON agent_messages (run_id, created_at);

CREATE TABLE model_usages (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    task_id BINARY(16) NULL,
    role VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    reasoning_tokens BIGINT NOT NULL DEFAULT 0,
    cached_input_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(18, 8) NOT NULL DEFAULT 0,
    estimated BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms BIGINT NOT NULL,
    request_status VARCHAR(32) NOT NULL,
    failure_type VARCHAR(128) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_model_usages_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_model_usages_task
        FOREIGN KEY (task_id) REFERENCES agent_tasks (id)
);
CREATE INDEX idx_model_usages_run_created ON model_usages (run_id, created_at);

CREATE TABLE tool_executions (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    task_id BINARY(16) NULL,
    tool_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    exit_code INT NULL,
    duration_ms BIGINT NOT NULL,
    output_artifact_id BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tool_executions_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_tool_executions_task
        FOREIGN KEY (task_id) REFERENCES agent_tasks (id)
);

CREATE TABLE artifacts (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    task_id BINARY(16) NULL,
    kind VARCHAR(64) NOT NULL,
    location VARCHAR(2048) NOT NULL,
    sha256 CHAR(64) NULL,
    size_bytes BIGINT NULL,
    metadata TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_artifacts_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_artifacts_task
        FOREIGN KEY (task_id) REFERENCES agent_tasks (id)
);
CREATE INDEX idx_artifacts_run_created ON artifacts (run_id, created_at);

CREATE TABLE approvals (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    task_id BINARY(16) NULL,
    action_type VARCHAR(128) NOT NULL,
    request_payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    decision_note TEXT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    decided_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_approvals_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    CONSTRAINT fk_approvals_task
        FOREIGN KEY (task_id) REFERENCES agent_tasks (id)
);
CREATE INDEX idx_approvals_run_status ON approvals (run_id, status);

CREATE TABLE outbox_messages (
    id BIGINT AUTO_INCREMENT NOT NULL,
    event_id BINARY(16) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    message_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    published_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (event_id)
);
CREATE INDEX idx_outbox_unpublished ON outbox_messages (published_at, id);
