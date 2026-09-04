package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;

/**
 * The provider boundary's secret-material guard: nothing leaves for the model provider without being checked against {@link HyperionSecretMaterialPolicy}.
 * <p>
 * Checking a whole {@link Prompt} rather than only its newest message is the point. Tool observations can introduce secret material at any turn, and the agent loop re-sends the
 * accumulated conversation on every call, so a message that was clean when it was appended still has to be clean when it is re-sent.
 */
final class AgentPromptSafety {

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private AgentPromptSafety() {
    }

    /** Checks every message of a prompt, descending into tool observations and assistant tool-call arguments. */
    static void requirePromptSafe(Prompt prompt) {
        int messageIndex = 0;
        for (Message message : prompt.getInstructions()) {
            if (message instanceof ToolResponseMessage toolResponse) {
                int responseIndex = 0;
                for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                    requireTextSafe("provider/tool-observation-" + messageIndex + "-" + responseIndex, response.responseData());
                    responseIndex++;
                }
            }
            else if (message instanceof AssistantMessage assistant) {
                requireAssistantSafe(assistant, "provider/message-" + messageIndex);
            }
            else {
                requireTextSafe("provider/message-" + messageIndex, message.getText());
            }
            messageIndex++;
        }
    }

    static void requireAssistantSafe(AssistantMessage assistant, String logicalPath) {
        requireTextSafe(logicalPath, assistant.getText());
        int toolCallIndex = 0;
        for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
            requireTextSafe(logicalPath + "-tool-call-" + toolCallIndex, toolCall.arguments());
            toolCallIndex++;
        }
    }

    static void requireTextSafe(String logicalPath, @Nullable String text) {
        SECRET_MATERIAL_POLICY.requireSafe(logicalPath, text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8), HyperionSecretMaterialPolicy.Origin.PROVIDER_PROMPT);
    }
}
