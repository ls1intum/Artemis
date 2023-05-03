package de.tum.in.www1.artemis.service.iris;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;

@Service
public class GPT3_5Service extends LLMService {

    /**
     * The URL to send the API requests to.
     * Defaults to <a href="https://api.openai.com/v1/chat/completions">...</a>.
     */
    @Value("${artemis.iris.gpt3_5.url:https://api.openai.com/v1/chat/completions}")
    private URL apiURL;

    /**
     * The API key to use when sending requests to the API.
     */
    @Value("${artemis.iris.gpt3_5.api-key}")
    private String apiKey;

    /**
     * The ID of the model to request from the API.
     * Defaults to "gpt-3.5-turbo".
     */
    @Value("${artemis.iris.gpt3_5.model:gpt-3.5-turbo}")
    private String model;

    /**
     * The temperature parameter controls the randomness of the model.
     * Lower temperatures make the model more deterministic, while higher temperatures make the model
     * more varied in its responses.
     * For a more detailed explanation, see <a href="https://platform.openai.com/docs/api-reference/completions/create">...</a>
     */
    @Value("${artemis.iris.gpt3_5.temperature}")
    private float temperature;

    /**
     * The maximum number of tokens that the model should generate in its responses.
     * This is a safety measure to prevent the model from returning too much data.
     */
    @Value("${artemis.iris.gpt3_5.max-tokens}")
    private int maxTokens;

    /**
     * Up to four sequences of characters that the model should stop generating text at.
     * This is a safety measure to prevent the model from generating too much text.
     */
    @Value("${artemis.iris.gpt3_5.stopSequences}")
    private String[] stopSequences;

    public GPT3_5Service(RestTemplate restTemplate) {
        super(restTemplate);
    }

    @Override
    protected URL getAPIUrl() {
        return apiURL;
    }

    @Override
    protected String getAPIKey() {
        return apiKey;
    }

    @Override
    protected Map<String, Object> createRequestParams(String systemInstructions, List<IrisMessage> conversationHistory) {
        return Map.of("model", model, "messages", convertMessages(systemInstructions, conversationHistory), "temperature", temperature, "max_tokens", maxTokens, "stop",
                stopSequences);
    }

    /**
     * Converts the system instructions and conversation history into GPT3_5's message format.
     *
     * @param systemInstructions  The system instructions to convert.
     * @param conversationHistory The conversation history to convert.
     * @return The converted messages.
     */
    private static List<Map<String, String>> convertMessages(String systemInstructions, List<IrisMessage> conversationHistory) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "message", systemInstructions));
        // TODO: Sanitize user input.
        conversationHistory.stream().map(GPT3_5Service::toRoleMessage).forEach(messages::add);
        return messages;
    }

    /**
     * Converts an IrisMessage into GPT3_5's message format, a map with the keys "role" and "message".
     * The value of "role" is either "user", "assistant" or "system", depending on the sender of the message.
     *
     * @param message The message to convert.
     * @return The converted message.
     */
    private static Map<String, String> toRoleMessage(IrisMessage message) {
        return Map.of("role", toRole(message.getSender()), "message", joinContents(message.getContent()));
    }

    /**
     * Converts an IrisMessageSender into GPT3_5's role format, either "user", "assistant" or "system".
     *
     * @param sender The sender to convert.
     * @return The equivalent role, either "user", "assistant" or "system".
     */
    private static String toRole(IrisMessageSender sender) {
        return switch (sender) {
            case USER -> "user";
            case LLM -> "assistant";
            case ARTEMIS -> "system"; // TODO: Remove this case as system instructions do not constitute a message.
        };
    }

    /**
     * Joins the text contents of a list of IrisMessageContent into a single string.
     * TODO: This needs to be reconsidered when we add support for other content types.
     *
     * @param messageContents The message contents to join.
     * @return The joined string.
     */
    private static String joinContents(List<IrisMessageContent> messageContents) {
        return messageContents.stream().map(IrisMessageContent::getTextContent).collect(Collectors.joining("\n"));
    }

}
