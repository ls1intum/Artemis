package de.tum.in.www1.artemis.service.iris;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisSession;

/**
 * Represents a service that sends requests to a LLM API.
 */
public abstract class LLMService {

    private final Logger log = LoggerFactory.getLogger(LLMService.class);

    /**
     * The RestTemplate used to send requests to the LLM API.
     * TODO: Needs to be configured?
     */
    private final RestTemplate restTemplate;

    /**
     * The system instructions that should be sent to the model at the start of the conversation.
     * This should never be sent to the user, and therefore should never be included in the messages list.
     */
    @Value("${artemis.iris.system-instructions}")
    private String systemInstructions;

    public LLMService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Sanitizes the user input before it is sent to the LLM API.
     *
     * @param input The user input to sanitize
     * @return The sanitized user input
     */
    protected static String sanitize(String input) {
        // TODO: Sanitize input (e.g. remove newlines, escape quotes)
        return input;
    }

    /**
     * Sends a request to the LLM API.
     * This method attempts to convert the provided body to a JSON string using Jackson.
     * If this fails, an internal server error is returned.
     *
     * @return The response from the LLM API
     */
    @Async
    public CompletableFuture<String> queryLLM(IrisSession session) {
        String jsonBody;
        try {
            Map<String, Object> requestParams = createRequestParams(systemInstructions, session.getMessages());
            jsonBody = new ObjectMapper().writeValueAsString(requestParams);
        }
        catch (JsonProcessingException e) {
            log.warn("Error while converting request parameters to JSON.", e);
            return CompletableFuture.failedFuture(e);
        }
        HttpEntity<String> request = new HttpEntity<>(jsonBody, createHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(getAPIUrl().toString(), request, String.class);
        return CompletableFuture.completedFuture(response.getBody());
    }

    /**
     * Gets a {@link Map} of request parameters to be sent to this LLM API.
     * These parameters will be converted into a JSON object and sent in the request body.
     * <p>
     * Different models require different request parameters, so this method must be overridden by subclasses
     * to create the parameters for the specific model.
     * <p>
     * Subclasses must use the provided conversation history so that the model can respond to the user's previous
     * messages. The system instructions should be included in the parameters as instructions to the model.
     *
     * @param systemInstructions  The instructions to send to the model, telling it how to respond to the user.
     *                                TODO: This could also include the exercise context.
     * @param conversationHistory The messages that have been sent in the conversation so far
     * @return The API request parameters
     */
    protected abstract Map<String, Object> createRequestParams(String systemInstructions, List<IrisMessage> conversationHistory);

    /**
     * Creates a HttpHeaders object with the Content-Type set to application/json and the Authorization header set to
     * the API key, which varies depending on the model used.
     *
     * @return A HttpHeaders object to be used in a request to a LLM API
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAPIKey());
        return headers;
    }

    /**
     * Gets the URL of this LLM API.
     *
     * @return The API URL
     */
    protected abstract URL getAPIUrl();

    /**
     * Gets the API bearer token to be used when sending requests to this LLM API.
     *
     * @return The API key
     */
    protected abstract String getAPIKey();

}
