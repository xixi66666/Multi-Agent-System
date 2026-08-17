package com.vibeagent.project;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProjectStore {

    private final JdbcTemplate jdbcTemplate;

    public ProjectStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Project create(String name, String rootPath, ProjectType type) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO projects (id, name, root_path, path_hash, project_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                uuidBytes(id),
                name,
                rootPath,
                pathHash(rootPath),
                type.name(),
                Timestamp.from(now),
                Timestamp.from(now));
        return require(id);
    }

    public Optional<Project> find(UUID id) {
        return jdbcTemplate.query(
                        "SELECT id, name, root_path, project_type, created_at, updated_at FROM projects WHERE id = ?",
                        (resultSet, rowNum) -> mapProject(resultSet),
                        uuidBytes(id))
                .stream()
                .findFirst();
    }

    public Optional<Project> findByRootPath(String rootPath) {
        return jdbcTemplate.query(
                        "SELECT id, name, root_path, project_type, created_at, updated_at FROM projects WHERE path_hash = ?",
                        (resultSet, rowNum) -> mapProject(resultSet),
                        pathHash(rootPath))
                .stream()
                .findFirst();
    }

    public List<Project> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, root_path, project_type, created_at, updated_at FROM projects ORDER BY created_at, name",
                (resultSet, rowNum) -> mapProject(resultSet));
    }

    private Project require(UUID id) {
        return find(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private Project mapProject(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new Project(
                fromUuidBytes(resultSet.getBytes("id")),
                resultSet.getString("name"),
                resultSet.getString("root_path"),
                ProjectType.valueOf(resultSet.getString("project_type")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static String pathHash(String path) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(path.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
