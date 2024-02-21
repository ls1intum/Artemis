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
import org.springframework.web.client.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.service.connectors.iris.dto.*;
import de.tum.in.www1.artemis.service.iris.exception.*;

/**
 * This service connects to the Python implementation of Iris (called Pyris) responsible for connecting to different
 * LLMs and handle messages with Microsoft Guidance
 */
@Service
@Profile("iris")
public class PyrisConnectorService {

    private static final Logger log = LoggerFactory.getLogger(PyrisConnectorService.class);

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Value("${artemis.iris.url}")
    private String pyrisUrl;

    public PyrisConnectorService(@Qualifier("pyrisRestTemplate") RestTemplate restTemplate, MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        this.restTemplate = restTemplate;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
    }

    /**
     * Requests all available models from Pyris
     *
     * @return A list of available Models as IrisModelDTO
     */
    public List<PyrisModelDTO> getOfferedModels() throws PyrisConnectorException {
        try {
            var response = restTemplate.getForEntity(pyrisUrl + "/api/v1/models", PyrisModelDTO[].class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                throw new PyrisConnectorException("Could not fetch offered models");
            }
            return Arrays.asList(response.getBody());
        }
        catch (HttpStatusCodeException e) {
            log.error("Failed to fetch offered models from Pyris", e);
            throw new PyrisConnectorException("Could not fetch offered models");
        }
    }

    @Async
    public CompletableFuture<PyrisJobDTO> executeTutorChatPipeline(String variant, PyrisTutorChatPipelineExecutionDTO executionDTO) {
        return executePipeline("tutor-chat", variant, executionDTO);
    }

    @Async
    public CompletableFuture<PyrisJobDTO> executeHestiaDescriptionGenerationPipeline(String variant, Object executionDTO) {
        return executePipeline("hestia-description-generation", variant, executionDTO);
    }

    @Async
    public CompletableFuture<PyrisJobDTO> executeCodeEditorPipeline(String variant, Object executionDTO) {
        return executePipeline("code-editor", variant, executionDTO);
    }

    @Async
    public CompletableFuture<PyrisJobDTO> executeCompetencyGenerationPipeline(String variant, Object executionDTO) {
        return executePipeline("competency-generation", variant, executionDTO);
    }

    private CompletableFuture<PyrisJobDTO> executePipeline(String feature, String variant, PyrisPipelineExecutionDTO executionDTO) {
        var endpoint = "/api/v1/pipelines/" + feature + "/" + variant + "/run";
        try {
            return sendRequestAndParseResponse(endpoint, executionDTO, PyrisJobDTO.class);
        }
        catch (JsonProcessingException e) {
            log.error("Failed to parse response from Pyris", e);
            return failedFuture(new IrisParseResponseException(e));
        }
        catch (HttpStatusCodeException e) {
            return failedFuture(toIrisException(e, null));
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send request to Pyris", e);
            return failedFuture(new PyrisConnectorException("Could not fetch response from Iris"));
        }
    }

    private <Response> CompletableFuture<Response> sendRequestAndParseResponse(String urlExtension, Object request, Class<Response> responseType) throws JsonProcessingException {
        var response = restTemplate.postForEntity(pyrisUrl + urlExtension, objectMapper.valueToTree(request), JsonNode.class);
        JsonNode body = response.getBody();
        if (body == null) {
            return failedFuture(new IrisNoResponseException());
        }
        Response parsed = objectMapper.treeToValue(body, responseType);
        return completedFuture(parsed);
    }

    private IrisException toIrisException(HttpStatusCodeException e, String preferredModel) {
        return switch (e.getStatusCode()) {
            case UNAUTHORIZED, FORBIDDEN -> new IrisForbiddenException();
            case BAD_REQUEST -> new IrisInvalidTemplateException(tryExtractErrorMessage(e));
            case NOT_FOUND -> new IrisModelNotAvailableException(preferredModel, tryExtractErrorMessage(e));
            case INTERNAL_SERVER_ERROR -> new IrisInternalPyrisErrorException(tryExtractErrorMessage(e));
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

    public boolean sendWebhook(String path, PyrisWebhookDTO dto) {
        try {
            restTemplate.postForEntity(pyrisUrl + "/api/v1/webhooks/" + path, dto, Void.class);
            return true;
        }
        catch (RestClientException e) {
            log.error("Failed to send webhook to Pyris", e);
            return false;
        }
    }
}
