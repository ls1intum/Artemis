package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

class AgentCheckpointMessageCodecTest {

    @Test
    void roundTripPreservesTheProviderConversationContract() {
        List<Message> conversation = List.of(SystemMessage.builder().text("system").metadata(Map.of("scope", "generation")).build(),
                UserMessage.builder().text("write it").metadata(Map.of("unicode", "ä")).build(),
                AssistantMessage.builder().content("").properties(Map.of("finish", "tool_calls"))
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "write_file", "{\"path\":\"SPEC.md\",\"content\":\"x\"}"),
                                new AssistantMessage.ToolCall("call-2", "function", "verify", "{}")))
                        .build(),
                ToolResponseMessage.builder().metadata(Map.of("round", 1)).responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "write_file", "Wrote 1 characters"),
                        new ToolResponseMessage.ToolResponse("call-2", "verify", "PASS"))).build(),
                new AssistantMessage("done"));

        List<Message> restored = AgentCheckpointMessageCodec.decode(AgentCheckpointMessageCodec.encode(conversation));

        assertThat(restored).isEqualTo(conversation);
    }
}
