package com.vibeagent.runtime;

import com.vibeagent.tool.SensitiveDataRedactor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class RepositoryContextService {

    private static final int MAX_TREE_ENTRIES = 600;
    private static final int MAX_FILE_CHARS = 30_000;
    private static final Set<String> IMPORTANT_FILES = Set.of(
            "AGENTS.md",
            "README.md",
            "pom.xml",
            "package.json",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts",
            "tsconfig.json");
    private static final Set<String> SKIPPED = Set.of(
            ".git", ".m2", ".tools", ".data", "node_modules", "target", "dist", "build");

    private final SensitiveDataRedactor redactor;

    public RepositoryContextService(SensitiveDataRedactor redactor) {
        this.redactor = redactor;
    }

    public String build(Path workspace) {
        Path root;
        try {
            root = workspace.toAbsolutePath().normalize().toRealPath();
        } catch (IOException exception) {
            return "Repository context unavailable: workspace does not exist.";
        }

        StringBuilder context = new StringBuilder("Repository tree:\n");
        List<Path> files = new ArrayList<>();
        try (var paths = Files.walk(root, 8)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !containsSkipped(root, path))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(MAX_TREE_ENTRIES)
                    .forEach(files::add);
        } catch (IOException exception) {
            return "Repository context unavailable: workspace cannot be scanned.";
        }
        for (Path file : files) {
            context.append(root.relativize(file).toString().replace('\\', '/')).append('\n');
        }

        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (!isImportant(relative)) {
                continue;
            }
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                context.append("\n--- ").append(relative).append(" ---\n")
                        .append(redactor.redact(truncate(content)));
            } catch (IOException ignored) {
                // A missing optional context file does not block the rest of repository discovery.
            }
        }
        return context.toString();
    }

    private boolean isImportant(String relativePath) {
        if (relativePath.equals("config/application-local.yml")
                || relativePath.equals("config/agent-models.local.yml")) {
            return false;
        }
        String fileName = Path.of(relativePath).getFileName().toString();
        return IMPORTANT_FILES.contains(fileName);
    }

    private boolean containsSkipped(Path root, Path path) {
        for (Path segment : root.relativize(path)) {
            if (SKIPPED.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value) {
        if (value.length() <= MAX_FILE_CHARS) {
            return value;
        }
        return value.substring(0, MAX_FILE_CHARS) + "\n[content truncated]";
    }
}
