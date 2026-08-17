package com.vibeagent.workspace;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TaskWorkspaceStore {

    private final JdbcTemplate jdbcTemplate;

    public TaskWorkspaceStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TaskWorkspace create(
            UUID runId,
            UUID taskId,
            WorkspaceType type,
            String path,
            String branchName,
            String baseRevision) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO run_workspaces (id, run_id, task_id, workspace_type, workspace_path, branch_name, "
                        + "base_revision, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                uuidBytes(id),
                uuidBytes(runId),
                taskId == null ? null : uuidBytes(taskId),
                type.name(),
                path,
                branchName,
                baseRevision,
                Timestamp.from(now));
        return require(id);
    }

    public Optional<TaskWorkspace> findIntegration(UUID runId) {
        return jdbcTemplate.query(
                        selectColumns() + " WHERE run_id = ? AND workspace_type = ? ORDER BY created_at LIMIT 1",
                        (resultSet, rowNum) -> mapWorkspace(resultSet),
                        uuidBytes(runId), WorkspaceType.INTEGRATION.name())
                .stream()
                .findFirst();
    }

    public Optional<TaskWorkspace> findByTask(UUID taskId) {
        return jdbcTemplate.query(
                        selectColumns() + " WHERE task_id = ? ORDER BY created_at LIMIT 1",
                        (resultSet, rowNum) -> mapWorkspace(resultSet),
                        uuidBytes(taskId))
                .stream()
                .findFirst();
    }

    public List<TaskWorkspace> findByRun(UUID runId) {
        return jdbcTemplate.query(
                selectColumns() + " WHERE run_id = ? ORDER BY created_at",
                (resultSet, rowNum) -> mapWorkspace(resultSet),
                uuidBytes(runId));
    }

    private TaskWorkspace require(UUID id) {
        return jdbcTemplate.query(
                        selectColumns() + " WHERE id = ?",
                        (resultSet, rowNum) -> mapWorkspace(resultSet),
                        uuidBytes(id))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Task workspace not found: " + id));
    }

    private String selectColumns() {
        return "SELECT id, run_id, task_id, workspace_type, workspace_path, branch_name, base_revision, created_at "
                + "FROM run_workspaces";
    }

    private TaskWorkspace mapWorkspace(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        byte[] taskBytes = resultSet.getBytes("task_id");
        return new TaskWorkspace(
                fromUuidBytes(resultSet.getBytes("id")),
                fromUuidBytes(resultSet.getBytes("run_id")),
                taskBytes == null ? null : fromUuidBytes(taskBytes),
                WorkspaceType.valueOf(resultSet.getString("workspace_type")),
                resultSet.getString("workspace_path"),
                resultSet.getString("branch_name"),
                resultSet.getString("base_revision"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private static byte[] uuidBytes(UUID id) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 2);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID fromUuidBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
