package com.vibeagent.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class RunEventStore {

    private final JdbcTemplate jdbcTemplate;

    public RunEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RunEvent append(UUID runId, String type, String payload) {
        Instant createdAt = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO run_events (run_id, event_type, payload, created_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setBytes(1, uuidBytes(runId));
            statement.setString(2, type);
            statement.setString(3, payload);
            statement.setTimestamp(4, Timestamp.from(createdAt));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return an ID for the run event");
        }
        return new RunEvent(key.longValue(), runId, type, payload, createdAt);
    }

    public List<RunEvent> findAfter(UUID runId, long afterId) {
        return jdbcTemplate.query(
                "SELECT id, run_id, event_type, payload, created_at FROM run_events "
                        + "WHERE run_id = ? AND id > ? ORDER BY id",
                (resultSet, rowNum) -> new RunEvent(
                        resultSet.getLong("id"),
                        fromUuidBytes(resultSet.getBytes("run_id")),
                        resultSet.getString("event_type"),
                        resultSet.getString("payload"),
                        resultSet.getTimestamp("created_at").toInstant()),
                uuidBytes(runId), afterId);
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
