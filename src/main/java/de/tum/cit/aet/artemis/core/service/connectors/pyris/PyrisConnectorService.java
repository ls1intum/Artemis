package de.tum.cit.aet.artemis.core.service.connectors.pyris;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.PyrisModelDTO;
import de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.exception.IrisException;
import de.tum.cit.aet.artemis.iris.exception.IrisForbiddenException;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.lti.web.open.PublicPyrisStatusUpdateResource;

/**
 * This service connects to the Python implementation of Iris (called Pyris).
 * Pyris is responsible for executing the pipelines using (MM)LLMs and other tools asynchronously.
 * Status updates are sent to Artemis via {@link PublicPyrisStatusUpdateResource}
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

    /**
     * Executes a pipeline with the given feature and variant
     *
     * @param feature      The feature name of the pipeline to execute
     * @param variant      The variant of the feature to execute
     * @param executionDTO The DTO sent as a body for the execution
     */
    public void executePipeline(String feature, String variant, Object executionDTO) {
        var endpoint = "/api/v1/pipelines/" + feature + "/" + variant + "/run";
        try {
            restTemplate.postForEntity(pyrisUrl + endpoint, objectMapper.valueToTree(executionDTO), Void.class);
        }
        catch (HttpStatusCodeException e) {
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send request to Pyris", e);
            throw new PyrisConnectorException("Could not fetch response from Iris");
        }
    }

    /**
     * Executes a webhook and send lectures to the webhook with the given variant
     *
     * @param variant      The variant of the feature to execute
     * @param executionDTO The DTO sent as a body for the execution
     */
    public void executeLectureWebhook(String variant, PyrisWebhookLectureIngestionExecutionDTO executionDTO) {
        var endpoint = "/api/v1/webhooks/lectures/" + variant;
        try {
            restTemplate.postForEntity(pyrisUrl + endpoint, objectMapper.valueToTree(executionDTO), Void.class);
        }
        catch (HttpStatusCodeException e) {
            log.error("Failed to send lectures to Pyris", e);
            throw toIrisException(e);
            // TODO : add error ingestion UI.
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send lectures to Pyris", e);
            throw new PyrisConnectorException("Could not fetch response from Pyris");
        }
    }

    private IrisException toIrisException(HttpStatusCodeException e) {
        return switch (e.getStatusCode().value()) {
            case 401, 403 -> new IrisForbiddenException();
            case 400, 500 -> new IrisInternalPyrisErrorException(tryExtractErrorMessage(e));
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
