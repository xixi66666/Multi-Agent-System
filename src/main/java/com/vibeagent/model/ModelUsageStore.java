package com.vibeagent.model;

import com.vibeagent.agent.AgentRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Component
public class ModelUsageStore {

    private final JdbcTemplate jdbcTemplate;

    public ModelUsageStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(ModelUsage usage) {
        jdbcTemplate.update(
                "INSERT INTO model_usages (id, run_id, task_id, role, provider, model, input_tokens, "
                        + "output_tokens, reasoning_tokens, cached_input_tokens, total_tokens, estimated_cost, "
                        + "estimated, latency_ms, request_status, failure_type, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                uuidBytes(usage.id()),
                uuidBytes(usage.runId()),
                usage.taskId() == null ? null : uuidBytes(usage.taskId()),
                usage.role().name(),
                usage.provider(),
                usage.model(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.reasoningTokens(),
                usage.cachedInputTokens(),
                usage.totalTokens(),
                usage.estimatedCost(),
                usage.estimated(),
                usage.latencyMillis(),
                usage.requestStatus(),
                usage.failureType(),
                Timestamp.from(usage.createdAt()));
    }

    public ModelUsageSummary summary(UUID runId) {
        List<ModelUsage> usages = findByRun(runId);
        return new ModelUsageSummary(
                runId,
                usages.size(),
                usages.stream().mapToLong(ModelUsage::inputTokens).sum(),
                usages.stream().mapToLong(ModelUsage::outputTokens).sum(),
                usages.stream().mapToLong(ModelUsage::reasoningTokens).sum(),
                usages.stream().mapToLong(ModelUsage::cachedInputTokens).sum(),
                usages.stream().mapToLong(ModelUsage::totalTokens).sum(),
                usages.stream().map(ModelUsage::estimatedCost).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                List.copyOf(usages));
    }

    public List<ModelUsage> findByRun(UUID runId) {
        return jdbcTemplate.query(
                "SELECT id, run_id, task_id, role, provider, model, input_tokens, output_tokens, reasoning_tokens, "
                        + "cached_input_tokens, total_tokens, estimated_cost, estimated, latency_ms, request_status, "
                        + "failure_type, created_at FROM model_usages WHERE run_id = ? ORDER BY created_at, id",
                (resultSet, rowNum) -> new ModelUsage(
                        fromUuidBytes(resultSet.getBytes("id")),
                        fromUuidBytes(resultSet.getBytes("run_id")),
                        resultSet.getBytes("task_id") == null ? null : fromUuidBytes(resultSet.getBytes("task_id")),
                        AgentRole.valueOf(resultSet.getString("role")),
                        resultSet.getString("provider"),
                        resultSet.getString("model"),
                        resultSet.getLong("input_tokens"),
                        resultSet.getLong("output_tokens"),
                        resultSet.getLong("reasoning_tokens"),
                        resultSet.getLong("cached_input_tokens"),
                        resultSet.getLong("total_tokens"),
                        resultSet.getBigDecimal("estimated_cost"),
                        resultSet.getBoolean("estimated"),
                        resultSet.getLong("latency_ms"),
                        resultSet.getString("request_status"),
                        resultSet.getString("failure_type"),
                        resultSet.getTimestamp("created_at").toInstant()),
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
