package de.tum.in.www1.artemis.service.connectors.iris;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisErrorResponseDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisMessageResponseDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisModelDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisRequestDTO;
import de.tum.in.www1.artemis.service.iris.exception.*;

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
     * @return The message response to the request which includes the {@link de.tum.in.www1.artemis.domain.iris.IrisMessage} and the used IrisModel
     */
    @Async
    public CompletableFuture<IrisMessageResponseDTO> sendRequest(IrisTemplate template, String preferredModel, Map<String, Object> parameters) {
        var request = new IrisRequestDTO(template, preferredModel, parameters);
        return sendRequest(request);
    }

    /**
     * Requests all available models from Pyris
     *
     * @return A list of available Models as IrisModelDTO
     */
    public List<IrisModelDTO> getOfferedModels() throws IrisConnectorException {
        try {
            var response = restTemplate.getForEntity(irisUrl + "/api/v1/models", JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                throw new IrisConnectorException("Could not fetch offered models");
            }
            return Arrays.asList((IrisModelDTO[]) parseResponse(response.getBody(), IrisModelDTO.class.arrayType()));
        }
        catch (HttpStatusCodeException e) {
            throw new IrisConnectorException("Could not fetch offered models");
        }
    }

    private CompletableFuture<IrisMessageResponseDTO> sendRequest(IrisRequestDTO request) {
        try {
            try {
                var response = restTemplate.postForEntity(irisUrl + "/api/v1/messages", objectMapper.valueToTree(request), JsonNode.class);
                if (!response.hasBody()) {
                    return CompletableFuture.failedFuture(new IrisNoResponseException());
                }
                return CompletableFuture.completedFuture(parseResponse(response.getBody(), IrisMessageResponseDTO.class));
            }
            catch (HttpStatusCodeException e) {
                switch (e.getStatusCode()) {
                    case BAD_REQUEST -> {
                        var badRequestDTO = parseResponse(objectMapper.readTree(e.getResponseBodyAsString()).get("detail"), IrisErrorResponseDTO.class);
                        return CompletableFuture.failedFuture(new IrisInvalidTemplateException(badRequestDTO.errorMessage()));
                    }
                    case UNAUTHORIZED, FORBIDDEN -> {
                        return CompletableFuture.failedFuture(new IrisForbiddenException());
                    }
                    case NOT_FOUND -> {
                        var notFoundDTO = parseResponse(objectMapper.readTree(e.getResponseBodyAsString()).get("detail"), IrisErrorResponseDTO.class);
                        return CompletableFuture.failedFuture(new IrisModelNotAvailableException(request.preferredModel().toString(), notFoundDTO.errorMessage()));
                    }
                    case INTERNAL_SERVER_ERROR -> {
                        var internalErrorDTO = parseResponse(objectMapper.readTree(e.getResponseBodyAsString()).get("detail"), IrisErrorResponseDTO.class);
                        return CompletableFuture.failedFuture(new IrisInternalPyrisErrorException(internalErrorDTO.errorMessage()));
                    }
                    default -> {
                        return CompletableFuture.failedFuture(new IrisInternalPyrisErrorException(e.getMessage()));
                    }
                }
            }
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private <T> T parseResponse(JsonNode response, Class<T> clazz) throws IrisParseResponseException {
        try {
            return objectMapper.treeToValue(response, clazz);
        }
        catch (JsonProcessingException e) {
            throw new IrisParseResponseException(e);
        }
    }
}
