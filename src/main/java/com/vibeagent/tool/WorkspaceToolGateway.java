package com.vibeagent.tool;

import com.vibeagent.agent.AgentRole;
import com.vibeagent.event.RunEventService;
import com.vibeagent.runtime.AgentTask;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkspaceToolGateway {

    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_LISTED_FILES = 1000;
    private static final int MAX_SEARCH_MATCHES = 200;
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".m2", ".tools", ".data", "node_modules", "target", "dist", "build");

    private final BoundedCommandRunner commandRunner;
    private final ToolExecutionStore executionStore;
    private final RunEventService runEventService;
    private final SensitiveDataRedactor redactor;
    private final WebDocumentReader webDocumentReader;

    public WorkspaceToolGateway(
            BoundedCommandRunner commandRunner,
            ToolExecutionStore executionStore,
            RunEventService runEventService,
            SensitiveDataRedactor redactor,
            WebDocumentReader webDocumentReader) {
        this.commandRunner = commandRunner;
        this.executionStore = executionStore;
        this.runEventService = runEventService;
        this.redactor = redactor;
        this.webDocumentReader = webDocumentReader;
    }

    public ToolResult execute(UUID runId, AgentTask task, Path workspace, AgentAction action) {
        if (action.action() == null || action.action() == ToolAction.COMPLETE) {
            throw new ToolPolicyViolationException("A concrete tool action is required");
        }
        requireAllowed(task.role(), action);
        long startedNanos = System.nanoTime();
        Integer exitCode = null;
        String status = "SUCCESS";
        try {
            ToolResult result = switch (action.action()) {
                case LIST_FILES -> listFiles(workspace, action.path());
                case READ_FILE -> readFile(workspace, action.path());
                case READ_URL -> {
                    ToolResult document = webDocumentReader.read(action.url());
                    yield new ToolResult(document.successful(), redactor.redact(document.output()), document.metadata());
                }
                case SEARCH_TEXT -> searchText(workspace, action.query());
                case WRITE_FILE -> writeFile(
                        workspace, action.path(), action.content(), action.expectedSha256(), task.role());
                case RUN_COMMAND -> {
                    CommandResult commandResult = commandRunner.run(action.command(), workspace);
                    exitCode = commandResult.exitCode();
                    yield new ToolResult(
                            commandResult.exitCode() == 0,
                            redactor.redact(commandResult.output()),
                            Map.of(
                                    "command", action.command().name(),
                                    "exitCode", commandResult.exitCode(),
                                    "durationMillis", commandResult.durationMillis()));
                }
                case COMPLETE -> throw new ToolPolicyViolationException("COMPLETE is not a tool call");
            };
            if (!result.successful()) {
                status = "FAILED";
            }
            return result;
        } catch (RuntimeException exception) {
            status = "FAILED";
            throw exception;
        } finally {
            long durationMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            String toolName = action.action() == ToolAction.RUN_COMMAND && action.command() != null
                    ? action.action().name() + ":" + action.command().name()
                    : action.action().name();
            ToolExecution execution = new ToolExecution(
                    UUID.randomUUID(), runId, task.id(), toolName, status, exitCode, durationMillis, Instant.now());
            executionStore.record(execution);
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("taskId", task.id());
            event.put("role", task.role().name());
            event.put("tool", action.action().name());
            event.put("status", status);
            event.put("durationMillis", durationMillis);
            if (exitCode != null) {
                event.put("exitCode", exitCode);
            }
            runEventService.publish(runId, "tool.executed", event);
        }
    }

    private ToolResult listFiles(Path workspace, String relativePath) {
        Path root = realRoot(workspace);
        Path start = resolveExisting(root, relativePath == null || relativePath.isBlank() ? "." : relativePath);
        if (!Files.isDirectory(start)) {
            throw new ToolPolicyViolationException("LIST_FILES path must be a directory");
        }
        List<String> files = new ArrayList<>();
        try (var paths = Files.walk(start, 8)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !containsSkippedDirectory(root, path))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(MAX_LISTED_FILES)
                    .forEach(path -> files.add(toRelative(root, path)));
        } catch (IOException exception) {
            throw new ToolPolicyViolationException("Workspace files cannot be listed");
        }
        return new ToolResult(true, String.join("\n", files), Map.of("count", files.size()));
    }

    private ToolResult readFile(Path workspace, String relativePath) {
        Path root = realRoot(workspace);
        Path file = resolveExisting(root, requireRelativePath(relativePath));
        requireReadableFile(root, file);
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                throw new ToolPolicyViolationException("File exceeds the read size limit");
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return new ToolResult(
                    true,
                    redactor.redact(content),
                    Map.of("path", toRelative(root, file), "sizeBytes", size, "sha256", sha256(Files.readAllBytes(file))));
        } catch (IOException exception) {
            throw new ToolPolicyViolationException("File cannot be read");
        }
    }

    private ToolResult searchText(Path workspace, String query) {
        if (query == null || query.isBlank() || query.length() > 200) {
            throw new ToolPolicyViolationException("SEARCH_TEXT requires a query of at most 200 characters");
        }
        Path root = realRoot(workspace);
        List<String> matches = new ArrayList<>();
        try (var paths = Files.walk(root, 12)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> !containsSkippedDirectory(root, path))
                    .toList()) {
                if (matches.size() >= MAX_SEARCH_MATCHES || Files.size(file) > MAX_FILE_BYTES || isDeniedPath(root, file)) {
                    continue;
                }
                List<String> lines;
                try {
                    lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    continue;
                }
                for (int index = 0; index < lines.size() && matches.size() < MAX_SEARCH_MATCHES; index++) {
                    if (lines.get(index).contains(query)) {
                        matches.add(toRelative(root, file) + ":" + (index + 1) + ":" + redactor.redact(lines.get(index)));
                    }
                }
            }
        } catch (IOException exception) {
            throw new ToolPolicyViolationException("Workspace search failed");
        }
        return new ToolResult(true, String.join("\n", matches), Map.of("matches", matches.size()));
    }

    private ToolResult writeFile(
            Path workspace,
            String relativePath,
            String content,
            String expectedSha256,
            AgentRole role) {
        if (content == null || content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new ToolPolicyViolationException("WRITE_FILE content is missing or exceeds the size limit");
        }
        if (redactor.containsSensitiveValue(content)) {
            throw new ToolPolicyViolationException("WRITE_FILE content appears to contain a credential or private key");
        }
        Path root = realRoot(workspace);
        Path target = resolveWritable(root, requireRelativePath(relativePath));
        requireWritablePath(root, target, role);
        try {
            if (Files.exists(target)) {
                String actualSha = sha256(Files.readAllBytes(target));
                if (expectedSha256 == null || !actualSha.equalsIgnoreCase(expectedSha256)) {
                    throw new ToolPolicyViolationException("Existing file changed or was not read before WRITE_FILE");
                }
            }
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".vibe-agent-" + UUID.randomUUID() + ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new ToolResult(
                    true,
                    "File written: " + toRelative(root, target),
                    Map.of("path", toRelative(root, target), "sha256", sha256(Files.readAllBytes(target))));
        } catch (IOException exception) {
            throw new ToolPolicyViolationException("File cannot be written");
        }
    }

    private void requireAllowed(AgentRole role, AgentAction action) {
        boolean readAction = action.action() == ToolAction.LIST_FILES
                || action.action() == ToolAction.READ_FILE
                || action.action() == ToolAction.SEARCH_TEXT;
        if (readAction) {
            return;
        }
        if (action.action() == ToolAction.READ_URL && role == AgentRole.RESEARCHER) {
            return;
        }
        if (action.action() == ToolAction.WRITE_FILE
                && (role == AgentRole.IMPLEMENTER || role == AgentRole.TESTER)) {
            return;
        }
        if (action.action() == ToolAction.RUN_COMMAND && commandAllowed(role, action.command())) {
            return;
        }
        throw new ToolPolicyViolationException("Agent role is not allowed to use this tool action");
    }

    private boolean commandAllowed(AgentRole role, AllowedCommand command) {
        if (command == null) {
            return false;
        }
        if (role == AgentRole.IMPLEMENTER || role == AgentRole.TESTER) {
            return true;
        }
        return (role == AgentRole.REVIEWER || role == AgentRole.INTEGRATOR)
                && (command == AllowedCommand.GIT_STATUS || command == AllowedCommand.GIT_DIFF);
    }

    private Path realRoot(Path workspace) {
        try {
            return workspace.toAbsolutePath().normalize().toRealPath();
        } catch (IOException exception) {
            throw new ToolPolicyViolationException("Workspace cannot be resolved");
        }
    }

    private Path resolveExisting(Path root, String relativePath) {
        Path candidate = resolveCandidate(root, relativePath);
        try {
            Path real = candidate.toRealPath();
            requireInside(root, real);
            return real;
        } catch (IOException exception) {
            throw new ToolPolicyViolationException("Requested workspace path does not exist");
        }
    }

    private Path resolveWritable(Path root, String relativePath) {
        Path candidate = resolveCandidate(root, relativePath);
        Path existing = Files.exists(candidate) ? candidate : candidate.getParent();
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new ToolPolicyViolationException("Writable path has no existing parent");
        }
        try {
            requireInside(root, existing.toRealPath());
            return candidate;
        } catch (IOException exception) {
            throw new ToolPolicyViolationException("Writable path cannot be resolved");
        }
    }

    private Path resolveCandidate(Path root, String relativePath) {
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) {
            throw new ToolPolicyViolationException("Tool paths must be relative to the task workspace");
        }
        Path candidate = root.resolve(relative).normalize();
        requireInside(root, candidate);
        return candidate;
    }

    private void requireReadableFile(Path root, Path file) {
        if (!Files.isRegularFile(file) || isDeniedPath(root, file)) {
            throw new ToolPolicyViolationException("File is not available to Agent tools");
        }
    }

    private void requireWritablePath(Path root, Path file, AgentRole role) {
        if (isDeniedPath(root, file)) {
            throw new ToolPolicyViolationException("File is protected from Agent tools");
        }
        if (role == AgentRole.TESTER) {
            String value = toRelative(root, file).toLowerCase(Locale.ROOT);
            boolean testPath = value.contains("src/test/")
                    || value.contains("/test/")
                    || value.contains("/tests/")
                    || value.endsWith(".test.ts")
                    || value.endsWith(".test.tsx")
                    || value.endsWith(".spec.ts")
                    || value.endsWith(".spec.tsx");
            if (!testPath) {
                throw new ToolPolicyViolationException("Tester may only write test files");
            }
        }
    }

    private boolean isDeniedPath(Path root, Path path) {
        String relative = toRelative(root, path).toLowerCase(Locale.ROOT);
        return relative.equals(".env")
                || relative.endsWith("/.env")
                || relative.equals("config/application-local.yml")
                || relative.equals("config/agent-models.local.yml")
                || relative.endsWith(".pem")
                || relative.endsWith(".key")
                || relative.contains("id_rsa")
                || relative.startsWith(".git/")
                || relative.equals(".git");
    }

    private boolean containsSkippedDirectory(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path segment : relative) {
            if (SKIPPED_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private void requireInside(Path root, Path path) {
        if (!path.equals(root) && !path.startsWith(root)) {
            throw new ToolPolicyViolationException("Tool path escaped the task workspace");
        }
    }

    private String requireRelativePath(String path) {
        if (path == null || path.isBlank()) {
            throw new ToolPolicyViolationException("Tool action requires a relative path");
        }
        return path;
    }

    private String toRelative(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
