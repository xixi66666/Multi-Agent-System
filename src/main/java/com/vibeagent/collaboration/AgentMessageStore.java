package com.vibeagent.collaboration;

import com.vibeagent.agent.AgentRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class AgentMessageStore {

    private final JdbcTemplate jdbcTemplate;

    public AgentMessageStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AgentMessage create(
            UUID runId,
            UUID taskId,
            AgentRole senderRole,
            AgentRole recipientRole,
            String messageType,
            String schemaVersion,
            String content) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO agent_messages (id, run_id, task_id, sender_role, recipient_role, message_type, "
                        + "schema_version, content, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                uuidBytes(id),
                uuidBytes(runId),
                taskId == null ? null : uuidBytes(taskId),
                senderRole.name(),
                recipientRole == null ? null : recipientRole.name(),
                messageType,
                schemaVersion,
                content,
                Timestamp.from(now));
        return new AgentMessage(
                id, runId, taskId, senderRole, recipientRole, messageType, schemaVersion, content, now);
    }

    public List<AgentMessage> findByRun(UUID runId) {
        return jdbcTemplate.query(
                "SELECT id, run_id, task_id, sender_role, recipient_role, message_type, schema_version, content, "
                        + "created_at FROM agent_messages WHERE run_id = ? ORDER BY created_at, id",
                (resultSet, rowNum) -> {
                    byte[] taskBytes = resultSet.getBytes("task_id");
                    String recipient = resultSet.getString("recipient_role");
                    return new AgentMessage(
                            fromUuidBytes(resultSet.getBytes("id")),
                            fromUuidBytes(resultSet.getBytes("run_id")),
                            taskBytes == null ? null : fromUuidBytes(taskBytes),
                            AgentRole.valueOf(resultSet.getString("sender_role")),
                            recipient == null ? null : AgentRole.valueOf(recipient),
                            resultSet.getString("message_type"),
                            resultSet.getString("schema_version"),
                            resultSet.getString("content"),
                            resultSet.getTimestamp("created_at").toInstant());
                },
                uuidBytes(runId));
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
