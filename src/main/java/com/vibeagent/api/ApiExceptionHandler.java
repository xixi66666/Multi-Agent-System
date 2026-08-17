package com.vibeagent.api;

import com.vibeagent.project.InvalidWorkspaceException;
import com.vibeagent.project.ProjectNotFoundException;
import com.vibeagent.run.RunNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({RunNotFoundException.class, ProjectNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> handleNotFound(RuntimeException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(InvalidWorkspaceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> handleInvalidWorkspace(InvalidWorkspaceException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> handleConflict(IllegalStateException exception) {
        return Map.of("error", exception.getMessage());
    }
}
