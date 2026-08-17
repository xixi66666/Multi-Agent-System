CREATE TABLE runs (
    id BINARY(16) NOT NULL,
    requirement TEXT NOT NULL,
    workspace_path VARCHAR(2048) NOT NULL,
    status VARCHAR(64) NOT NULL,
    summary TEXT NULL,
    failure TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_runs_status_updated (status, updated_at)
);

CREATE TABLE agent_results (
    run_id BINARY(16) NOT NULL,
    role VARCHAR(64) NOT NULL,
    successful BOOLEAN NOT NULL,
    summary TEXT NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (run_id, role),
    CONSTRAINT fk_agent_results_run
        FOREIGN KEY (run_id) REFERENCES runs (id)
);

CREATE TABLE run_events (
    id BIGINT AUTO_INCREMENT NOT NULL,
    run_id BINARY(16) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_run_events_run
        FOREIGN KEY (run_id) REFERENCES runs (id),
    INDEX idx_run_events_run_id (run_id, id)
);
