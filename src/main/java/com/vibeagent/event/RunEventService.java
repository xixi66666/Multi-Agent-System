package com.vibeagent.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RunEventService {

    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final RunEventStore runEventStore;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public RunEventService(RunEventStore runEventStore, ObjectMapper objectMapper) {
        this.runEventStore = runEventStore;
        this.objectMapper = objectMapper;
    }

    public RunEvent publish(UUID runId, String type, Object payload) {
        RunEvent event = runEventStore.append(runId, type, toJson(payload));
        for (SseEmitter emitter : emitters.getOrDefault(runId, new CopyOnWriteArrayList<>())) {
            sendOrRemove(runId, emitter, event);
        }
        return event;
    }

    public List<RunEvent> eventsAfter(UUID runId, long afterId) {
        return runEventStore.findAfter(runId, afterId);
    }

    public SseEmitter subscribe(UUID runId, long afterId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        CopyOnWriteArrayList<SseEmitter> runEmitters = emitters.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>());
        runEmitters.add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(error -> remove(runId, emitter));

        for (RunEvent event : eventsAfter(runId, afterId)) {
            sendOrRemove(runId, emitter, event);
        }
        return emitter;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Run event payload is not serializable", exception);
        }
    }

    private void sendOrRemove(UUID runId, SseEmitter emitter, RunEvent event) {
        try {
            emitter.send(SseEmitter.event().id(Long.toString(event.id())).name("run-event").data(event));
        } catch (IOException | IllegalStateException exception) {
            remove(runId, emitter);
        }
    }

    private void remove(UUID runId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> runEmitters = emitters.get(runId);
        if (runEmitters == null) {
            return;
        }
        runEmitters.remove(emitter);
        if (runEmitters.isEmpty()) {
            emitters.remove(runId, runEmitters);
        }
    }
}
