package com.vibeagent.tool;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class BoundedCommandRunner {

    private static final int MAX_OUTPUT_BYTES = 256 * 1024;

    private final Path applicationRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    private final boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows");

    public CommandResult run(AllowedCommand command, Path workspace) {
        Path root = requireWorkspace(workspace);
        CommandSpec spec = commandSpec(command, root);
        long startedNanos = System.nanoTime();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(spec.command());
            builder.directory(root.toFile());
            builder.redirectErrorStream(true);
            sanitizeEnvironment(builder.environment());
            process = builder.start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Process runningProcess = process;
            Thread outputReader = Thread.ofVirtual().start(() -> readBounded(runningProcess, output));
            boolean completed = process.waitFor(spec.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                destroyProcessTree(process);
                outputReader.join();
                throw new ToolPolicyViolationException("Allowed command timed out: " + command);
            }
            outputReader.join();
            return new CommandResult(
                    process.exitValue(),
                    output.toString(StandardCharsets.UTF_8),
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        } catch (IOException exception) {
            throw new ToolPolicyViolationException("Allowed command executable is unavailable: " + command);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                destroyProcessTree(process);
            }
            throw new ToolPolicyViolationException("Allowed command was interrupted: " + command);
        }
    }

    private CommandSpec commandSpec(AllowedCommand command, Path workspace) {
        return switch (command) {
            case MAVEN_TEST -> new CommandSpec(mavenCommand(workspace, "test"), Duration.ofMinutes(10));
            case MAVEN_PACKAGE -> new CommandSpec(mavenCommand(workspace, "package"), Duration.ofMinutes(10));
            case NPM_TEST -> new CommandSpec(npmCommand("test"), Duration.ofMinutes(10));
            case NPM_BUILD -> new CommandSpec(npmCommand("run", "build"), Duration.ofMinutes(10));
            case GIT_STATUS -> new CommandSpec(
                    List.of("git", "-C", workspace.toString(), "status", "--short"), Duration.ofMinutes(2));
            case GIT_DIFF -> new CommandSpec(
                    List.of("git", "-C", workspace.toString(), "diff", "--no-ext-diff", "--"), Duration.ofMinutes(2));
        };
    }

    private List<String> mavenCommand(Path workspace, String goal) {
        Path wrapper = workspace.resolve(windows ? "mvnw.cmd" : "mvnw");
        if (!Files.isRegularFile(wrapper)) {
            throw new ToolPolicyViolationException("Registered Maven command requires a project Maven Wrapper");
        }
        String localRepository = applicationRoot.resolve(".m2/repository").toString();
        if (windows) {
            return windowsBatch(wrapper.toString(), "-Dmaven.repo.local=" + localRepository, goal);
        }
        return List.of(wrapper.toString(), "-Dmaven.repo.local=" + localRepository, goal);
    }

    private List<String> npmCommand(String... arguments) {
        if (!windows) {
            List<String> command = new ArrayList<>();
            command.add("npm");
            command.addAll(List.of(arguments));
            return command;
        }
        return windowsBatch("npm.cmd", arguments);
    }

    private List<String> windowsBatch(String executable, String... arguments) {
        StringBuilder command = new StringBuilder("call ").append(quoteWindowsArgument(executable));
        for (String argument : arguments) {
            command.append(' ').append(quoteWindowsArgument(argument));
        }
        return List.of("cmd.exe", "/d", "/s", "/c", command.toString());
    }

    private String quoteWindowsArgument(String value) {
        if (value.indexOf('"') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new ToolPolicyViolationException("Unsafe character in registered command path");
        }
        return '"' + value + '"';
    }

    private Path requireWorkspace(Path workspace) {
        try {
            Path resolved = workspace.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(resolved)) {
                throw new ToolPolicyViolationException("Tool workspace is not a directory");
            }
            return resolved;
        } catch (IOException exception) {
            throw new ToolPolicyViolationException("Tool workspace cannot be resolved");
        }
    }

    private void sanitizeEnvironment(Map<String, String> environment) {
        Map<String, String> source = Map.copyOf(environment);
        environment.clear();
        copyEnvironment(source, environment, "PATH", "Path", "SystemRoot", "TEMP", "TMP");
        Path jdk = applicationRoot.resolve(".tools/jdk-21");
        if (Files.isDirectory(jdk)) {
            environment.put("JAVA_HOME", jdk.toString());
            String pathKey = environment.containsKey("Path") ? "Path" : "PATH";
            String currentPath = environment.getOrDefault(pathKey, "");
            environment.put(pathKey, jdk.resolve("bin") + java.io.File.pathSeparator + currentPath);
        }
        environment.put("MAVEN_USER_HOME", applicationRoot.resolve(".m2").toString());
        environment.put("NPM_CONFIG_CACHE", applicationRoot.resolve(".npm-cache").toString());
    }

    private void copyEnvironment(Map<String, String> source, Map<String, String> target, String... keys) {
        for (String key : keys) {
            String value = source.get(key);
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private void readBounded(Process process, ByteArrayOutputStream output) {
        try (var input = process.getInputStream()) {
            byte[] buffer = new byte[8192];
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
            if (written == MAX_OUTPUT_BYTES) {
                output.write("\n[output truncated]".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // The command result remains authoritative if its output stream closes early.
        }
    }

    private void destroyProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private record CommandSpec(List<String> command, Duration timeout) {
    }
}
