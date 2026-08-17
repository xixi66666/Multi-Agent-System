package com.vibeagent.api;

import com.vibeagent.project.Project;
import com.vibeagent.project.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project create(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.register(request.name(), request.rootPath(), request.type());
    }

    @GetMapping
    public List<Project> list() {
        return projectService.list();
    }

    @GetMapping("/{id}")
    public Project get(@PathVariable UUID id) {
        return projectService.get(id);
    }
}
