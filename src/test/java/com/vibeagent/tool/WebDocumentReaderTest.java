package com.vibeagent.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebDocumentReaderTest {

    private final WebDocumentReader reader = new WebDocumentReader();

    @Test
    void rejectsNonHttpsAndPrivateNetworkUrls() {
        assertThatThrownBy(() -> reader.validate("http://example.com/docs"))
                .isInstanceOf(ToolPolicyViolationException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> reader.validate("https://127.0.0.1/docs"))
                .isInstanceOf(ToolPolicyViolationException.class)
                .hasMessageContaining("private");
        assertThatThrownBy(() -> reader.validate("https://user@example.com/docs"))
                .isInstanceOf(ToolPolicyViolationException.class)
                .hasMessageContaining("HTTPS");
    }
}
