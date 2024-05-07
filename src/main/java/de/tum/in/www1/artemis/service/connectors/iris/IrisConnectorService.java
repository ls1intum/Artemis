package de.tum.in.www1.artemis.service.connectors.iris;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisMessageResponseV2DTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisModelDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisRequestV2DTO;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;
import de.tum.in.www1.artemis.service.iris.exception.IrisForbiddenException;
import de.tum.in.www1.artemis.service.iris.exception.IrisInternalPyrisErrorException;
import de.tum.in.www1.artemis.service.iris.exception.IrisInvalidTemplateException;
import de.tum.in.www1.artemis.service.iris.exception.IrisModelNotAvailableException;
import de.tum.in.www1.artemis.service.iris.exception.IrisNoResponseException;
import de.tum.in.www1.artemis.service.iris.exception.IrisParseResponseException;

/**
 * This service connects to the Python implementation of Iris (called Pyris) responsible for connecting to different
 * LLMs and handle messages with Microsoft Guidance
 */
@Service
@Profile("iris")
public class IrisConnectorService {

    private static final Logger log = LoggerFactory.getLogger(IrisConnectorService.class);

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Value("${artemis.iris.url}")
    private String irisUrl;

    public IrisConnectorService(@Qualifier("irisRestTemplate") RestTemplate restTemplate, MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        this.restTemplate = restTemplate;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
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
            IrisModelDTO[] models = objectMapper.treeToValue(response.getBody(), IrisModelDTO[].class);
            return Arrays.asList(models);
        }
        catch (HttpStatusCodeException | JsonProcessingException e) {
            log.error("Failed to fetch offered models from Pyris", e);
            throw new IrisConnectorException("Could not fetch offered models");
        }
    }

    /**
     * Requests a response from Pyris using the V2 Messages API
     *
     * @param template       The guidance program to execute
     * @param preferredModel The LLM model to be used (e.g., GPT3.5-turbo). Note: The used model might not be the
     *                           preferred model (e.g., if an error occurs or the preferredModel is not reachable)
     * @param argumentsDTO   A map of argument variables required for the guidance template (if they are specified in
     *                           the template)
     * @return The response of the type {@link IrisMessageResponseV2DTO}
     */
    @Async
    public CompletableFuture<IrisMessageResponseV2DTO> sendRequestV2(String template, String preferredModel, Object argumentsDTO) {
        var endpoint = "/api/v2/messages";
        var request = new IrisRequestV2DTO(template, preferredModel, argumentsDTO);
        return tryGetResponse(endpoint, request, preferredModel, IrisMessageResponseV2DTO.class);
    }

    private <T> CompletableFuture<T> tryGetResponse(String endpoint, Object request, String preferredModel, Class<T> responseType) {
        try {
            return sendRequestAndParseResponse(endpoint, request, responseType);
        }
        catch (JsonProcessingException e) {
            log.error("Failed to parse response from Pyris", e);
            return failedFuture(new IrisParseResponseException(e));
        }
        catch (HttpStatusCodeException e) {
            return failedFuture(toIrisException(e, preferredModel));
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send request to Pyris", e);
            return failedFuture(new IrisConnectorException("Could not fetch response from Iris"));
        }
    }

    private <Response> CompletableFuture<Response> sendRequestAndParseResponse(String urlExtension, Object request, Class<Response> responseType) throws JsonProcessingException {
        var response = restTemplate.postForEntity(irisUrl + urlExtension, objectMapper.valueToTree(request), JsonNode.class);
        JsonNode body = response.getBody();
        if (body == null) {
            return failedFuture(new IrisNoResponseException());
        }
        Response parsed = objectMapper.treeToValue(body, responseType);
        return completedFuture(parsed);
    }

    private IrisException toIrisException(HttpStatusCodeException e, String preferredModel) {
        return switch (e.getStatusCode().value()) {
            case 401, 403 -> new IrisForbiddenException();
            case 400 -> new IrisInvalidTemplateException(tryExtractErrorMessage(e));
            case 404 -> new IrisModelNotAvailableException(preferredModel, tryExtractErrorMessage(e));
            case 500 -> new IrisInternalPyrisErrorException(tryExtractErrorMessage(e));
            default -> new IrisInternalPyrisErrorException(e.getMessage());
        };
    }

    private String tryExtractErrorMessage(HttpStatusCodeException ex) {
        try {
            return objectMapper.readTree(ex.getResponseBodyAsString()).required("detail").required("errorMessage").asText();
        }
        catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse error message from Pyris", e);
            return "";
        }
    }
}
