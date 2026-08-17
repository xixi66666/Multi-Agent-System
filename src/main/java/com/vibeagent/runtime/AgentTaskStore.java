package com.vibeagent.runtime;

import com.vibeagent.agent.AgentRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AgentTaskStore {

    private final JdbcTemplate jdbcTemplate;

    public AgentTaskStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AgentTask create(
            UUID runId,
            UUID parentTaskId,
            AgentRole role,
            String specialty,
            String title,
            String instructions,
            int maxAttempts) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO agent_tasks (id, run_id, parent_task_id, role, specialty, title, instructions, status, "
                        + "attempt, max_attempts, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                uuidBytes(id),
                uuidBytes(runId),
                parentTaskId == null ? null : uuidBytes(parentTaskId),
                role.name(),
                specialty,
                title,
                instructions,
                AgentTaskStatus.PENDING.name(),
                0,
                maxAttempts,
                Timestamp.from(now),
                Timestamp.from(now));
        return require(id);
    }

    public AgentTask start(UUID id, String workerId, Duration leaseDuration) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
                "UPDATE agent_tasks SET status = ?, attempt = attempt + 1, lease_owner = ?, lease_until = ?, "
                        + "updated_at = ? WHERE id = ? AND status = ? AND attempt < max_attempts",
                AgentTaskStatus.RUNNING.name(),
                workerId,
                Timestamp.from(now.plus(leaseDuration)),
                Timestamp.from(now),
                uuidBytes(id),
                AgentTaskStatus.PENDING.name());
        if (updated != 1) {
            throw new IllegalStateException("Agent task is not claimable: " + id);
        }
        return require(id);
    }

    public AgentTask complete(UUID id, String resultSummary) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
                "UPDATE agent_tasks SET status = ?, result_summary = ?, failure = NULL, lease_owner = NULL, "
                        + "lease_until = NULL, updated_at = ? WHERE id = ? AND status = ?",
                AgentTaskStatus.COMPLETED.name(),
                resultSummary,
                Timestamp.from(now),
                uuidBytes(id),
                AgentTaskStatus.RUNNING.name());
        if (updated != 1) {
            throw new IllegalStateException("Agent task is not running: " + id);
        }
        return require(id);
    }

    public AgentTask fail(UUID id, String failure) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "UPDATE agent_tasks SET status = ?, failure = ?, lease_owner = NULL, lease_until = NULL, "
                        + "updated_at = ? WHERE id = ?",
                AgentTaskStatus.FAILED.name(),
                failure,
                Timestamp.from(now),
                uuidBytes(id));
        return require(id);
    }

    public Optional<AgentTask> find(UUID id) {
        List<AgentTask> tasks = jdbcTemplate.query(
                selectColumns() + " WHERE id = ?",
                (resultSet, rowNum) -> mapTask(resultSet),
                uuidBytes(id));
        return tasks.stream().findFirst();
    }

    public List<AgentTask> findByRun(UUID runId) {
        return jdbcTemplate.query(
                selectColumns() + " WHERE run_id = ? ORDER BY created_at, id",
                (resultSet, rowNum) -> mapTask(resultSet),
                uuidBytes(runId));
    }

    private AgentTask require(UUID id) {
        return find(id).orElseThrow(() -> new IllegalStateException("Agent task not found: " + id));
    }

    private AgentTask mapTask(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        byte[] parentBytes = resultSet.getBytes("parent_task_id");
        Timestamp leaseUntil = resultSet.getTimestamp("lease_until");
        return new AgentTask(
                fromUuidBytes(resultSet.getBytes("id")),
                fromUuidBytes(resultSet.getBytes("run_id")),
                parentBytes == null ? null : fromUuidBytes(parentBytes),
                AgentRole.valueOf(resultSet.getString("role")),
                resultSet.getString("specialty"),
                resultSet.getString("title"),
                resultSet.getString("instructions"),
                AgentTaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt"),
                resultSet.getInt("max_attempts"),
                resultSet.getString("lease_owner"),
                leaseUntil == null ? null : leaseUntil.toInstant(),
                resultSet.getString("result_summary"),
                resultSet.getString("failure"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private String selectColumns() {
        return "SELECT id, run_id, parent_task_id, role, specialty, title, instructions, status, attempt, "
                + "max_attempts, lease_owner, lease_until, result_summary, failure, created_at, updated_at "
                + "FROM agent_tasks";
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
