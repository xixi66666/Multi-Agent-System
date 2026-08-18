package com.vibeagent.runtime;

import com.vibeagent.tool.AgentAction;
import com.vibeagent.tool.ToolAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredActionParserTest {

    private final StructuredActionParser parser = new StructuredActionParser();

    @Test
    void parsesPlainJson() {
        AgentAction action = parser.parse("{\"action\":\"LIST_FILES\",\"path\":\".\"}");
        assertThat(action.action()).isEqualTo(ToolAction.LIST_FILES);
        assertThat(action.path()).isEqualTo(".");
    }

    @Test
    void parsesJsonInsideMarkdownFence() {
        AgentAction action = parser.parse("```json\n{\"action\":\"SEARCH_TEXT\",\"query\":\"last-third-days\"}\n```");
        assertThat(action.action()).isEqualTo(ToolAction.SEARCH_TEXT);
        assertThat(action.query()).isEqualTo("last-third-days");
    }

    @Test
    void parsesJsonSurroundedByProse() {
        AgentAction action = parser.parse("I will search the codebase.\n{\"action\":\"SEARCH_TEXT\",\"query\":\"x\"}\nDone.");
        assertThat(action.action()).isEqualTo(ToolAction.SEARCH_TEXT);
        assertThat(action.query()).isEqualTo("x");
    }

    @Test
    void parsesFirstObjectWhenMultipleJsonObjectsArePresent() {
        AgentAction action = parser.parse(
                "Two options: {\"action\":\"LIST_FILES\",\"path\":\".\"} or {\"action\":\"READ_FILE\",\"path\":\"pom.xml\"}");
        assertThat(action.action()).isEqualTo(ToolAction.LIST_FILES);
    }

    @Test
    void parsesJsonFollowedByExplanationContainingBraces() {
        AgentAction action = parser.parse(
                "{\"action\":\"READ_URL\",\"url\":\"https://example.com/doc\"} then check the {docs} section");
        assertThat(action.action()).isEqualTo(ToolAction.READ_URL);
    }

    @Test
    void parsesJsonWithWindowsPathValue() {
        AgentAction action = parser.parse("{\"action\":\"READ_FILE\",\"path\":\"backend\\\\app\\\\main.py\"}");
        assertThat(action.action()).isEqualTo(ToolAction.READ_FILE);
        assertThat(action.path()).isEqualTo("backend\\app\\main.py");
    }

    @Test
    void rejectsContentWithoutJson() {
        assertThatThrownBy(() -> parser.parse("I will start by exploring the workspace."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Agent response was not a valid structured action");
    }

    @Test
    void rejectsNullContent() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Agent response was not a valid structured action");
    }

    @Test
    void errorIncludesPreviewOfInvalidContent() {
        assertThatThrownBy(() -> parser.parse("explanation {"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explanation {");
    }

    @Test
    void rejectsJsonWithoutActionType() {
        assertThatThrownBy(() -> parser.parse("{\"path\":\".\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Agent action type is required");
    }

    @Test
    void rejectsUnterminatedJsonObject() {
        assertThatThrownBy(() -> parser.parse("{\"action\":\"LIST_FILES\""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Agent response was not a valid structured action");
    }

    @Test
    void parsesJsonWithBlankEnumValues() {
        AgentAction action = parser.parse("{\n"
                + "  \"action\": \"LIST_FILES\",\n"
                + "  \"path\": \".\",\n"
                + "  \"url\": \"\",\n"
                + "  \"query\": \"\",\n"
                + "  \"content\": \"\",\n"
                + "  \"expectedSha256\": \"\",\n"
                + "  \"command\": \"\",\n"
                + "  \"summary\": \"List files in the workspace.\"\n"
                + "}");
        assertThat(action.action()).isEqualTo(ToolAction.LIST_FILES);
        assertThat(action.path()).isEqualTo(".");
        assertThat(action.command()).isNull();
    }

    @Test
    void ignoresUnknownProperties() {
        AgentAction action = parser.parse("{\"action\":\"READ_FILE\",\"path\":\"pom.xml\",\"extra\":123}");
        assertThat(action.action()).isEqualTo(ToolAction.READ_FILE);
        assertThat(action.path()).isEqualTo("pom.xml");
    }
}
