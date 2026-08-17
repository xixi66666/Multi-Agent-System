package com.vibeagent.tool;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class ToolExecutionStore {

    private final JdbcTemplate jdbcTemplate;

    public ToolExecutionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(ToolExecution execution) {
        jdbcTemplate.update(
                "INSERT INTO tool_executions (id, run_id, task_id, tool_name, status, exit_code, duration_ms, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                uuidBytes(execution.id()),
                uuidBytes(execution.runId()),
                execution.taskId() == null ? null : uuidBytes(execution.taskId()),
                execution.toolName(),
                execution.status(),
                execution.exitCode(),
                execution.durationMillis(),
                Timestamp.from(execution.createdAt()));
    }

    public boolean hasSuccessfulVerificationCommand(UUID taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tool_executions WHERE task_id = ? AND status = ? "
                        + "AND tool_name IN (?, ?, ?, ?)",
                Integer.class,
                uuidBytes(taskId),
                "SUCCESS",
                "RUN_COMMAND:MAVEN_TEST",
                "RUN_COMMAND:MAVEN_PACKAGE",
                "RUN_COMMAND:NPM_TEST",
                "RUN_COMMAND:NPM_BUILD");
        return count != null && count > 0;
    }

    private static byte[] uuidBytes(UUID id) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 2);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return buffer.array();
    }
}
