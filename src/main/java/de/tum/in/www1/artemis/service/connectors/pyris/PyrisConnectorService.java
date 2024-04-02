package de.tum.in.www1.artemis.service.connectors.pyris;

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
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.*;
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

    void executePipeline(String feature, String variant, PyrisPipelineExecutionDTO executionDTO) {
        var endpoint = "/api/v1/pipelines/" + feature + "/" + variant + "/run";
        try {
            sendRequestAndParseResponse(endpoint, executionDTO, Void.class);
        }
        catch (JsonProcessingException e) {
            log.error("Failed to parse response from Pyris", e);
            throw new IrisParseResponseException(e);
        }
        catch (HttpStatusCodeException e) {
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send request to Pyris", e);
            throw new PyrisConnectorException("Could not fetch response from Iris");
        }
    }

    private <Response> CompletableFuture<Response> sendRequestAndParseResponse(String urlExtension, Object request, Class<Response> responseType) throws JsonProcessingException {
        var response = restTemplate.postForEntity(pyrisUrl + urlExtension, objectMapper.valueToTree(request), JsonNode.class);
        JsonNode body = response.getBody();
        if (body == null) {
            if (responseType == Void.class) {
                return completedFuture(null);
            }
            return failedFuture(new IrisNoResponseException());
        }
        Response parsed = objectMapper.treeToValue(body, responseType);
        return completedFuture(parsed);
    }

    private IrisException toIrisException(HttpStatusCodeException e) {
        return switch (e.getStatusCode().value()) {
            case 401, 403 -> new IrisForbiddenException();
            case 400 -> new IrisInvalidTemplateException(tryExtractErrorMessage(e));
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
