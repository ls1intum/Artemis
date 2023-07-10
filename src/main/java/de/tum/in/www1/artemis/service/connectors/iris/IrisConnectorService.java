package de.tum.in.www1.artemis.service.connectors.iris;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisMessageResponseDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisRequestDTO;

/**
 * This service connects to the Python implementation of Iris (called Pyris) responsible for connecting to different
 * LLMs and handle messages with Microsoft Guidance
 */
@Service
@Profile("iris")
public class IrisConnectorService {

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Value("${artemis.iris.url}")
    private String irisUrl;

    public IrisConnectorService(@Qualifier("irisRestTemplate") RestTemplate restTemplate, MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        this.restTemplate = restTemplate;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
    }

    /**
     * Requests a response from an LLM
     *
     * @param template       The template that should be used with the respective parameters (e.g., for initial system message)
     * @param preferredModel The LLM model to be used (e.g., GPT3.5-turbo). Note: The used model might not be the preferred model (e.g., if an error occurs or the preferredModel is
     *                           not reachable)
     * @param parameters     A map of parameters to be included in the template through handlebars (if they are specified
     *                           in the template)
     * @return The message response to the request which includes the {@link de.tum.in.www1.artemis.domain.iris.IrisMessage} and the used {@link IrisModel}
     */
    @Async
    public CompletableFuture<IrisMessageResponseDTO> sendRequest(IrisTemplate template, IrisModel preferredModel, Map<String, Object> parameters) {
        var request = new IrisRequestDTO(template, preferredModel, parameters);
        return sendRequest(request);
    }

    private CompletableFuture<IrisMessageResponseDTO> sendRequest(IrisRequestDTO request) {
        var response = restTemplate.postForEntity(irisUrl + "/api/v1/messages", objectMapper.valueToTree(request), JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            return CompletableFuture.failedFuture(new IrisConnectorException(/* parseResponse(response, IrisErrorResponseDTO.class).errorMessage() */"Could not receive response"));
        }
        return CompletableFuture.completedFuture(parseResponse(response, IrisMessageResponseDTO.class));
    }

    private <T> T parseResponse(ResponseEntity<JsonNode> response, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(response.getBody(), clazz);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
