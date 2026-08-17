package com.vibeagent.run;

import com.vibeagent.agent.AgentResult;
import com.vibeagent.agent.AgentRole;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RunStore {

    private final ConcurrentMap<UUID, CodingRun> runs = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;

    /** Used by focused unit tests that do not create a Spring database. */
    public RunStore() {
        this.jdbcTemplate = null;
    }

    @Autowired
    public RunStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    CodingRun create(String requirement, String workspace) {
        return create(requirement, workspace, null);
    }

    CodingRun create(String requirement, String workspace, UUID projectId) {
        CodingRun run = new CodingRun(requirement, workspace, projectId);
        save(run);
        return run;
    }

    Optional<CodingRun> find(UUID id) {
        CodingRun inMemory = runs.get(id);
        if (inMemory != null || jdbcTemplate == null) {
            return Optional.ofNullable(inMemory);
        }

        List<CodingRun> persisted = jdbcTemplate.query(
                "SELECT id, project_id, requirement, workspace_path, status, summary, failure, created_at, updated_at "
                        + "FROM runs WHERE id = ?",
                (resultSet, rowNum) -> readRun(resultSet),
                uuidBytes(id));
        if (persisted.isEmpty()) {
            return Optional.empty();
        }

        CodingRun run = restoreWithResults(persisted.getFirst());
        runs.put(id, run);
        return Optional.of(run);
    }

    List<RunSnapshot> findAll() {
        if (jdbcTemplate == null) {
            return runs.values().stream()
                    .map(CodingRun::snapshot)
                    .sorted(java.util.Comparator.comparing(RunSnapshot::createdAt).reversed())
                    .toList();
        }
        List<UUID> ids = jdbcTemplate.query(
                "SELECT id FROM runs ORDER BY created_at DESC",
                (resultSet, rowNum) -> fromUuidBytes(resultSet.getBytes("id")));
        return ids.stream()
                .map(this::find)
                .flatMap(Optional::stream)
                .map(CodingRun::snapshot)
                .toList();
    }

    void save(CodingRun run) {
        runs.put(run.id(), run);
        if (jdbcTemplate == null) {
            return;
        }

        RunSnapshot snapshot = run.snapshot();
        byte[] idBytes = uuidBytes(snapshot.id());
        Timestamp createdAt = Timestamp.from(snapshot.createdAt());
        Timestamp updatedAt = Timestamp.from(snapshot.updatedAt());

        int updated = jdbcTemplate.update(
                "UPDATE runs SET requirement = ?, workspace_path = ?, status = ?, summary = ?, failure = ?, "
                        + "updated_at = ? WHERE id = ?",
                snapshot.requirement(), snapshot.workspace(), snapshot.status().name(), snapshot.summary(),
                snapshot.failure(), updatedAt, idBytes);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO runs (id, project_id, requirement, workspace_path, status, summary, failure, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    idBytes,
                    snapshot.projectId() == null ? null : uuidBytes(snapshot.projectId()),
                    snapshot.requirement(), snapshot.workspace(), snapshot.status().name(), snapshot.summary(),
                    snapshot.failure(), createdAt, updatedAt);
        }

        jdbcTemplate.update("DELETE FROM agent_results WHERE run_id = ?", idBytes);
        for (AgentResult result : snapshot.results().values()) {
            jdbcTemplate.update(
                    "INSERT INTO agent_results (run_id, role, successful, summary, completed_at) VALUES (?, ?, ?, ?, ?)",
                    idBytes,
                    result.role().name(),
                    result.successful(),
                    result.summary(),
                    Timestamp.from(result.completedAt()));
        }
    }

    private CodingRun restoreWithResults(CodingRun persisted) {
        Map<AgentRole, AgentResult> results = new EnumMap<>(AgentRole.class);
        jdbcTemplate.query(
                "SELECT role, successful, summary, completed_at FROM agent_results WHERE run_id = ?",
                resultSet -> {
                    AgentRole role = AgentRole.valueOf(resultSet.getString("role"));
                    results.put(role, new AgentResult(
                            role,
                            resultSet.getBoolean("successful"),
                            resultSet.getString("summary"),
                            resultSet.getTimestamp("completed_at").toInstant()));
                },
                uuidBytes(persisted.id()));
        RunSnapshot snapshot = persisted.snapshot();
        return CodingRun.restore(
                persisted.id(),
                snapshot.projectId(),
                persisted.requirement(),
                persisted.workspace(),
                snapshot.status(),
                snapshot.summary(),
                snapshot.failure(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                results);
    }

    private CodingRun readRun(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        byte[] projectBytes = resultSet.getBytes("project_id");
        return CodingRun.restore(
                fromUuidBytes(resultSet.getBytes("id")),
                projectBytes == null ? null : fromUuidBytes(projectBytes),
                resultSet.getString("requirement"),
                resultSet.getString("workspace_path"),
                RunStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("summary"),
                resultSet.getString("failure"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                Map.of());
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
