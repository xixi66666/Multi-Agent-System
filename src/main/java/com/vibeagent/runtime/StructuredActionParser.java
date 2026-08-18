package com.vibeagent.runtime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.vibeagent.tool.AgentAction;
import com.vibeagent.tool.AllowedCommand;
import com.vibeagent.tool.ToolAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class StructuredActionParser {

    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private static final int MAX_PREVIEW_CHARS = 2_000;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .registerModule(new SimpleModule()
                    .addDeserializer(ToolAction.class, lenientEnum(ToolAction.class))
                    .addDeserializer(AllowedCommand.class, lenientEnum(AllowedCommand.class)));

    public AgentAction parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Agent response was not a valid structured action: <empty>");
        }
        String trimmed = content.trim();
        String withoutFences = FENCE.matcher(trimmed).replaceAll("$1").trim();
        boolean parsedWithoutAction = false;
        for (String shape : new String[] { trimmed, withoutFences }) {
            AgentAction action = tryParse(shape);
            if (action != null) {
                if (action.action() != null) {
                    return action;
                }
                parsedWithoutAction = true;
            }
        }
        for (int start = trimmed.indexOf('{'); start >= 0 && start < trimmed.length();) {
            int end = closingBrace(trimmed, start);
            if (end > start) {
                AgentAction action = tryParse(trimmed.substring(start, end + 1));
                if (action != null) {
                    if (action.action() != null) {
                        return action;
                    }
                    parsedWithoutAction = true;
                }
                start = trimmed.indexOf('{', end + 1);
            } else {
                start = trimmed.indexOf('{', start + 1);
            }
        }
        if (parsedWithoutAction) {
            throw new IllegalStateException("Agent action type is required");
        }
        throw new IllegalStateException("Agent response was not a valid structured action: " + preview(content));
    }

    private AgentAction tryParse(String candidate) {
        try {
            return objectMapper.readValue(candidate, AgentAction.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return null;
        }
    }

    private int closingBrace(String content, int start) {
        int depth = 0;
        for (int index = start; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private String preview(String content) {
        if (content.length() <= MAX_PREVIEW_CHARS) {
            return content;
        }
        return content.substring(0, MAX_PREVIEW_CHARS) + "\n[truncated]";
    }

    private static <T extends Enum<T>> JsonDeserializer<T> lenientEnum(Class<T> type) {
        final T[] constants = type.getEnumConstants();
        return new JsonDeserializer<T>() {
            @Override
            public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                String value = parser.getValueAsString();
                if (value == null || value.isBlank()) {
                    return null;
                }
                for (T constant : constants) {
                    if (constant.name().equalsIgnoreCase(value)) {
                        return constant;
                    }
                }
                return null;
            }
        };
    }
}
