package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Stable, lossless representation of the text-only Spring AI messages used by the generation agent. Checkpoints do not serialize Spring implementation classes: their Jackson
 * shape is not a public compatibility contract.
 */
final class AgentCheckpointMessageCodec {

    private AgentCheckpointMessageCodec() {
    }

    record ToolCall(String id, String type, String name, String arguments) {
    }

    record ToolResponse(String id, String name, String data) {
    }

    record RecordedMessage(String role, String text, Map<String, Object> metadata, List<ToolCall> toolCalls, List<ToolResponse> toolResponses) {
    }

    static List<RecordedMessage> encode(List<Message> messages) {
        return messages.stream().map(AgentCheckpointMessageCodec::encode).toList();
    }

    static List<Message> decode(List<RecordedMessage> messages) {
        return messages.stream().map(AgentCheckpointMessageCodec::decode).toList();
    }

    private static RecordedMessage encode(Message message) {
        Map<String, Object> metadata = Map.copyOf(message.getMetadata());
        return switch (message) {
            case SystemMessage system -> new RecordedMessage("system", system.getText(), metadata, List.of(), List.of());
            case UserMessage user when user.getMedia().isEmpty() -> new RecordedMessage("user", user.getText(), metadata, List.of(), List.of());
            case UserMessage ignored -> throw new IllegalArgumentException("Agent checkpoints do not support user-message media.");
            case AssistantMessage assistant when assistant.getMedia().isEmpty() -> new RecordedMessage("assistant", assistant.getText(), metadata,
                    assistant.getToolCalls().stream().map(call -> new ToolCall(call.id(), call.type(), call.name(), call.arguments())).toList(), List.of());
            case AssistantMessage ignored -> throw new IllegalArgumentException("Agent checkpoints do not support assistant-message media.");
            case ToolResponseMessage tool -> new RecordedMessage("tool", tool.getText(), metadata, List.of(),
                    tool.getResponses().stream().map(response -> new ToolResponse(response.id(), response.name(), response.responseData())).toList());
            default -> throw new IllegalArgumentException("Unsupported checkpoint message type: " + message.getClass().getName());
        };
    }

    private static Message decode(RecordedMessage message) {
        return switch (message.role()) {
            case "system" -> SystemMessage.builder().text(message.text()).metadata(message.metadata()).build();
            case "user" -> UserMessage.builder().text(message.text()).metadata(message.metadata()).build();
            case "assistant" -> AssistantMessage.builder().content(message.text()).properties(message.metadata())
                    .toolCalls(message.toolCalls().stream().map(call -> new AssistantMessage.ToolCall(call.id(), call.type(), call.name(), call.arguments())).toList()).build();
            case "tool" -> ToolResponseMessage.builder().metadata(message.metadata())
                    .responses(message.toolResponses().stream().map(response -> new ToolResponseMessage.ToolResponse(response.id(), response.name(), response.data())).toList())
                    .build();
            default -> throw new IllegalArgumentException("Unsupported checkpoint message role: " + message.role());
        };
    }
}
