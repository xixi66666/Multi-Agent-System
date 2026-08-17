package com.vibeagent.tool;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SensitiveDataRedactor {

    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|password|secret|authorization)"
                    + "(\\s*[:=]\\s*[\\\"']?)([^\\s\\\"']{8,})");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");

    public String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String redacted = PRIVATE_KEY.matcher(value).replaceAll("<REDACTED_PRIVATE_KEY>");
        redacted = BEARER.matcher(redacted).replaceAll("Bearer <REDACTED>");
        return NAMED_SECRET.matcher(redacted).replaceAll("$1$2<REDACTED>");
    }

    public boolean containsSensitiveValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.replace("YOUR_API_KEY_HERE", "")
                .replace("YOUR_PASSWORD_HERE", "")
                .replace("YOUR_TOKEN_HERE", "")
                .replace("<REDACTED>", "");
        return PRIVATE_KEY.matcher(normalized).find()
                || BEARER.matcher(normalized).find()
                || NAMED_SECRET.matcher(normalized).find();
    }
}
