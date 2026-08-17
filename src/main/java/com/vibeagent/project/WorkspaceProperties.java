package com.vibeagent.project;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "vibe.workspace")
public class WorkspaceProperties {

    private List<String> allowedRoots = new ArrayList<>(List.of("."));
    private String worktreeRoot = ".worktrees";

    public List<String> getAllowedRoots() {
        return allowedRoots;
    }

    public void setAllowedRoots(List<String> allowedRoots) {
        this.allowedRoots = allowedRoots;
    }

    public String getWorktreeRoot() {
        return worktreeRoot;
    }

    public void setWorktreeRoot(String worktreeRoot) {
        this.worktreeRoot = worktreeRoot;
    }
}
