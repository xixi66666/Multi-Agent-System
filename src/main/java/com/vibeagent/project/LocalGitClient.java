package com.vibeagent.project;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class LocalGitClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    public boolean isRepository(Path directory) {
        CommandResult result = run(List.of("git", "-C", directory.toString(), "rev-parse", "--is-inside-work-tree"));
        return result.exitCode() == 0 && result.output().trim().equals("true");
    }

    public void initialize(Path directory) {
        requireSuccess(run(List.of("git", "-C", directory.toString(), "init")), "Git repository initialization failed");
        requireSuccess(run(List.of(
                "git",
                "-C",
                directory.toString(),
                "-c",
                "user.name=Vibe Agent",
                "-c",
                "user.email=vibe-agent@local",
                "commit",
                "--allow-empty",
                "-m",
                "Initialize autonomous project")), "Initial Git commit failed");
    }

    public void createWorktree(Path repository, Path target, String branchName, String startPoint) {
        requireSuccess(run(List.of(
                "git",
                "-C",
                repository.toString(),
                "worktree",
                "add",
                "-b",
                branchName,
                target.toString(),
                startPoint)), "Git worktree creation failed");
    }

    public boolean commitChanges(Path worktree, String message) {
        CommandResult status = run(List.of("git", "-C", worktree.toString(), "status", "--porcelain"));
        requireSuccess(status, "Git status failed");
        if (status.output().isBlank()) {
            return false;
        }
        requireSuccess(run(List.of("git", "-C", worktree.toString(), "add", "--all")), "Git staging failed");
        requireSuccess(run(List.of(
                "git",
                "-C",
                worktree.toString(),
                "-c",
                "user.name=Vibe Agent",
                "-c",
                "user.email=vibe-agent@local",
                "commit",
                "-m",
                message)), "Git commit failed");
        return true;
    }

    public void merge(Path integrationWorktree, String branchName, String message) {
        requireSuccess(run(List.of(
                "git",
                "-C",
                integrationWorktree.toString(),
                "-c",
                "user.name=Vibe Agent",
                "-c",
                "user.email=vibe-agent@local",
                "merge",
                "--no-ff",
                branchName,
                "-m",
                message)), "Git task branch integration failed");
    }

    public String originUrl(Path worktree) {
        CommandResult result = run(List.of("git", "-C", worktree.toString(), "remote", "get-url", "origin"));
        requireSuccess(result, "Git origin remote is not configured");
        String remote = result.output().trim();
        if (remote.isBlank()) {
            throw new InvalidWorkspaceException("Git origin remote is empty");
        }
        if (remote.matches("https?://[^/@]+@.*")) {
            throw new InvalidWorkspaceException("Credential-bearing Git remote URLs are not supported");
        }
        return remote;
    }

    public void push(Path worktree, String branchName) {
        if (!branchName.matches("[A-Za-z0-9._/-]{1,255}")) {
            throw new InvalidWorkspaceException("Git branch name is not valid for an approved push");
        }
        requireSuccess(run(List.of(
                "git", "-C", worktree.toString(), "push", "origin", branchName)), "Approved Git push failed");
    }

    public String headRevision(Path worktree) {
        CommandResult result = run(List.of("git", "-C", worktree.toString(), "rev-parse", "HEAD"));
        requireSuccess(result, "Git HEAD revision cannot be resolved");
        String revision = result.output().trim();
        if (!revision.matches("[0-9a-fA-F]{40,64}")) {
            throw new InvalidWorkspaceException("Git returned an invalid HEAD revision");
        }
        return revision;
    }

    public String diffAgainst(Path worktree, String baseRevision) {
        if (baseRevision == null || !baseRevision.matches("[0-9a-fA-F]{40,64}")) {
            throw new InvalidWorkspaceException("Git base revision is invalid");
        }
        CommandResult result = run(List.of(
                "git", "-C", worktree.toString(), "diff", "--no-ext-diff", baseRevision, "--"));
        requireSuccess(result, "Git final diff cannot be generated");
        return result.output();
    }

    private CommandResult run(List<String> command) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
            builder.redirectErrorStream(true);
            Map<String, String> sourceEnvironment = System.getenv();
            Map<String, String> environment = builder.environment();
            environment.clear();
            copyEnvironment(sourceEnvironment, environment, "PATH", "Path", "SystemRoot", "TEMP", "TMP");
            process = builder.start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Process runningProcess = process;
            Thread outputReader = Thread.ofVirtual().start(() -> readBounded(runningProcess, output));
            boolean completed = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                outputReader.join();
                throw new InvalidWorkspaceException("Git command timed out");
            }
            outputReader.join();
            return new CommandResult(process.exitValue(), output.toString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new InvalidWorkspaceException("Git executable is not available");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new InvalidWorkspaceException("Git command was interrupted");
        }
    }

    private void readBounded(Process process, ByteArrayOutputStream output) {
        try (var input = process.getInputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            int written = 0;
            while ((read = input.read(buffer)) >= 0) {
                int remaining = MAX_OUTPUT_BYTES - written;
                if (remaining > 0) {
                    int length = Math.min(remaining, read);
                    output.write(buffer, 0, length);
                    written += length;
                }
            }
        } catch (IOException ignored) {
            // Process exit handling reports command failure without exposing raw stream errors.
        }
    }

    private void copyEnvironment(Map<String, String> source, Map<String, String> target, String... keys) {
        for (String key : keys) {
            String value = source.get(key);
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private void requireSuccess(CommandResult result, String message) {
        if (result.exitCode() != 0) {
            throw new InvalidWorkspaceException(message);
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
