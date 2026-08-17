package com.vibeagent.approval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ApprovalStore {

    private final JdbcTemplate jdbcTemplate;

    public ApprovalStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Approval create(UUID runId, UUID taskId, String actionType, String requestPayload) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO approvals (id, run_id, task_id, action_type, request_payload, status, requested_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                uuidBytes(id),
                uuidBytes(runId),
                taskId == null ? null : uuidBytes(taskId),
                actionType,
                requestPayload,
                ApprovalStatus.PENDING.name(),
                Timestamp.from(now));
        return require(id);
    }

    public List<Approval> findByRun(UUID runId) {
        return jdbcTemplate.query(
                selectColumns() + " WHERE run_id = ? ORDER BY requested_at DESC",
                (resultSet, rowNum) -> mapApproval(resultSet),
                uuidBytes(runId));
    }

    public Optional<Approval> find(UUID id) {
        return jdbcTemplate.query(
                        selectColumns() + " WHERE id = ?",
                        (resultSet, rowNum) -> mapApproval(resultSet),
                        uuidBytes(id))
                .stream()
                .findFirst();
    }

    public Approval decide(UUID id, ApprovalStatus status, String note) {
        if (status != ApprovalStatus.APPROVED && status != ApprovalStatus.REJECTED) {
            throw new IllegalArgumentException("Approval decision must be APPROVED or REJECTED");
        }
        int updated = jdbcTemplate.update(
                "UPDATE approvals SET status = ?, decision_note = ?, decided_at = ? WHERE id = ? AND status = ?",
                status.name(),
                note,
                Timestamp.from(Instant.now()),
                uuidBytes(id),
                ApprovalStatus.PENDING.name());
        if (updated != 1) {
            throw new IllegalStateException("Approval is not pending: " + id);
        }
        return require(id);
    }

    public Approval finish(UUID id, ApprovalStatus status, String note) {
        if (status != ApprovalStatus.EXECUTED && status != ApprovalStatus.FAILED) {
            throw new IllegalArgumentException("Approval execution status must be EXECUTED or FAILED");
        }
        int updated = jdbcTemplate.update(
                "UPDATE approvals SET status = ?, decision_note = ?, decided_at = ? WHERE id = ? AND status = ?",
                status.name(),
                note,
                Timestamp.from(Instant.now()),
                uuidBytes(id),
                ApprovalStatus.APPROVED.name());
        if (updated != 1) {
            throw new IllegalStateException("Approval is not approved: " + id);
        }
        return require(id);
    }

    private Approval require(UUID id) {
        return find(id).orElseThrow(() -> new IllegalStateException("Approval not found: " + id));
    }

    private String selectColumns() {
        return "SELECT id, run_id, task_id, action_type, request_payload, status, decision_note, requested_at, decided_at "
                + "FROM approvals";
    }

    private Approval mapApproval(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        byte[] taskBytes = resultSet.getBytes("task_id");
        Timestamp decidedAt = resultSet.getTimestamp("decided_at");
        return new Approval(
                fromUuidBytes(resultSet.getBytes("id")),
                fromUuidBytes(resultSet.getBytes("run_id")),
                taskBytes == null ? null : fromUuidBytes(taskBytes),
                resultSet.getString("action_type"),
                resultSet.getString("request_payload"),
                ApprovalStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("decision_note"),
                resultSet.getTimestamp("requested_at").toInstant(),
                decidedAt == null ? null : decidedAt.toInstant());
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
