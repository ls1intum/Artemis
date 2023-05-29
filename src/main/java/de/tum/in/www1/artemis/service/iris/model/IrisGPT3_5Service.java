package de.tum.in.www1.artemis.service.iris.model;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.service.dto.OpenAIChatResponseDTO;

/**
 * A service which sends REST requests to the GPT-3.5 API to generate responses to Iris conversations.
 */
@Service
@Profile("iris-gpt3_5")
public class IrisGPT3_5Service implements IrisModel {

    private final Logger log = LoggerFactory.getLogger(IrisGPT3_5Service.class);

    /**
     * The RestTemplate used to send requests to the LLM API.
     * TODO: Needs to be configured?
     */
    private final RestTemplate restTemplate;

    /**
     * The URL to send the API requests to.
     */
    @Value("${artemis.iris.models.gpt3_5.url}")
    private URL apiURL;

    /**
     * The API key to use when sending requests to the API.
     */
    @Value("${artemis.iris.models.gpt3_5.api-key}")
    private String apiKey;

    /**
     * The ID of the model to request from the API.
     */
    @Value("${artemis.iris.models.gpt3_5.model}")
    private String model;

    /**
     * The temperature parameter controls the randomness of the model. Lower temperatures make the model more
     * deterministic, while higher temperatures make the model more varied in its responses. For a more detailed
     * explanation, see <a href="https://platform.openai.com/docs/api-reference/completions/create">...</a>
     */
    @Value("${artemis.iris.models.gpt3_5.temperature}")
    private float temperature;

    /**
     * The maximum number of tokens that the model should generate in its responses. This is a safety measure to prevent
     * the model from returning too much data.
     */
    @Value("${artemis.iris.models.gpt3_5.max-generated-tokens}")
    private int maxGeneratedTokens;

    /**
     * Up to four sequences of characters that the model should stop generating text at. This is a safety measure to
     * prevent the model from generating too much text.
     */
    @Value("${artemis.iris.models.gpt3_5.stop-sequences}")
    private String[] stopSequences;

    public IrisGPT3_5Service(@Qualifier("gpt35RestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Prompts GPT-3.5 to generate a response to the conversation in the provided {@link IrisSession}.
     *
     * @param session The session to generate a response for.
     * @return A {@link CompletableFuture} that will be completed with the response.
     */
    @Override
    @Async
    public CompletableFuture<IrisMessage> getResponse(IrisSession session) {
        try {
            Map<String, Object> requestParams = createRequestParams(session.getMessages());
            ObjectMapper jsonMapper = new ObjectMapper();
            String requestBody = jsonMapper.writeValueAsString(requestParams);
            log.debug("Request body: {}", requestBody);

            HttpEntity<String> request = new HttpEntity<>(requestBody, createHeaders());
            String responseBody = restTemplate.postForObject(apiURL.toString(), request, String.class);
            log.debug("Response body: {}", responseBody);

            // TODO: Create DTO for error responses, and handle them
            OpenAIChatResponseDTO response = jsonMapper.readValue(responseBody, OpenAIChatResponseDTO.class);
            var irisMessage = convertToIrisMessage(response.getChoices().get(0).getMessage());
            return CompletableFuture.completedFuture(irisMessage);
        }
        catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Returns a format string used as the initial system prompt for storing in the database
     *
     * @return Initial system message for this LLM as format string
     */
    @Override
    public String getInitialSystemMessageTemplate() {
        return "This will be the initial system prompt...";
    }

    /**
     * Converts the response from the GPT-3.5 API into an {@link IrisMessage} object.
     *
     * @param message the response from the GPT-3.5 API
     * @return the converted {@link IrisMessage}
     */
    private IrisMessage convertToIrisMessage(OpenAIChatResponseDTO.Message message) {
        var content = new IrisMessageContent();
        content.setTextContent(message.getContent());
        var irisMessage = new IrisMessage();
        irisMessage.setSender(IrisMessageSender.LLM);
        irisMessage.setSentAt(ZonedDateTime.now());
        irisMessage.setContent(List.of(content));

        return irisMessage;
    }

    /**
     * Gets a {@link Map} of request parameters to be sent to GPT-3.5. These parameters will be converted into a
     * JSON object and sent in the request body.
     *
     * @param conversationHistory The messages that have been sent in the conversation so far
     * @return The API request parameters
     */
    private Map<String, Object> createRequestParams(List<IrisMessage> conversationHistory) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("model", model);
        parameters.put("messages", toOpenAIFormat(conversationHistory));
        parameters.put("temperature", temperature);
        parameters.put("max_tokens", maxGeneratedTokens);
        if (stopSequences.length > 0)
            parameters.put("stop", stopSequences);
        return parameters;
    }

    /**
     * Creates a HttpHeaders object with the Content-Type set to application/json and the Authorization header set to
     * the API key, which varies depending on the model used.
     *
     * @return A HttpHeaders object to be used in a request to a LLM API
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);
        return headers;
    }

    /**
     * Converts a list of IrisMessages into OpenAI's message format.
     *
     * @param conversationHistory The conversation history to convert.
     * @return The converted messages.
     */
    private static List<Map<String, String>> toOpenAIFormat(List<IrisMessage> conversationHistory) {
        return conversationHistory.stream().map(IrisGPT3_5Service::toOpenAIFormat).toList();
    }

    /**
     * Converts a single IrisMessage into OpenAI's message format, a map with the keys "role" and "content".
     * The value of "role" is either "user", "assistant" or "system", depending on the sender of the message.
     *
     * @param message The message to convert.
     * @return The converted message.
     */
    private static Map<String, String> toOpenAIFormat(IrisMessage message) {
        // @formatter:off
        return Map.of(
            "role", toRole(message.getSender()),
            "content", joinContents(message.getContent())
        );
        // @formatter:on
    }

    /**
     * Converts an IrisMessageSender into OpenAI's role format, either "user", "assistant" or "system".
     *
     * @param sender The sender to convert.
     * @return The equivalent OpenAI role, either "user", "assistant" or "system".
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
