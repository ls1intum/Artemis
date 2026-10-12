package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;

class AgentToolProgressTest {

    @Test
    void decodesJsonEscapesBeforeSanitizingProgress() {
        String arguments = "{\"path\":\"solution/" + "\\u0053" + "tack\\nTest.java\"}";
        assertThat(describe(arguments)).isEqualTo("Reviewing solution/Stack Test.java.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "{\"metadata\":{\"path\":\"unrelated.java\"}}", "{\"path\":42}", "{\"path\":null}", "{\"path\":\"incomplete", "not JSON", "{\"path\":\" \\n\\t \"}" })
    void malformedOrNonStringPathsDoNotInventAFile(String arguments) {
        assertThat(describe(arguments)).isEqualTo("Reviewing an exercise file.");
    }

    @ParameterizedTest
    @CsvSource({ "write_file,Working on src/File.java.", "edit_file,Working on src/File.java.", "delete_file,Removing src/File.java.", "bash,Running a workspace command.",
            "verify,Checking the exercise.", "submit,Submitting the current work for checking.", "unknown_tool,Continuing the exercise update." })
    void describesKnownActionsWithoutExposingCommands(String name, String expected) {
        var call = new AssistantMessage.ToolCall("call", "function", name, "{\"path\":\"src/File.java\",\"command\":\"private command content\"}");

        assertThat(AgentToolProgress.describe(call)).isEqualTo(expected).doesNotContain("private command content");
    }

    @Test
    void redactsTokenBearingPathsAndTruncatesLongUnicodePathsWithoutSplittingCharacters() {
        String token = "ghp_" + "A".repeat(36);
        assertThat(describe("{\"path\":\"src/" + token + "\"}")).isEqualTo("Reviewing <redacted-path>.").doesNotContain(token);
        assertThat(describe("{\"path\":\"" + "😀".repeat(200) + "\"}")).isEqualTo("Reviewing " + "😀".repeat(159) + "….");
    }

    private static String describe(String arguments) {
        return AgentToolProgress.describe(new AssistantMessage.ToolCall("call", "function", "read_file", arguments));
    }
}
