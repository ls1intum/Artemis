package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;

class AgentToolProgressTest {

    @Test
    void decodesJsonEscapesBeforeSanitizingProgress() {
        String arguments = "{\"path\":\"solution/" + "\\u0053" + "tack\\nTest.java\"}";
        assertThat(describe(arguments)).isEqualTo("Reviewing solution/Stack Test.java.");
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"metadata\":{\"path\":\"unrelated.java\"}}", "{\"path\":42}", "{\"path\":null}", "{\"path\":\"incomplete", "not JSON" })
    void malformedOrNonStringPathsDoNotInventAFile(String arguments) {
        assertThat(describe(arguments)).isEqualTo("Reviewing an exercise file.");
    }

    private static String describe(String arguments) {
        return AgentToolProgress.describe(new AssistantMessage.ToolCall("call", "function", "read_file", arguments));
    }
}
