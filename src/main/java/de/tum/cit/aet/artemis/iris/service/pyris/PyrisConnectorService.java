package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;
import de.tum.cit.aet.artemis.iris.dto.IngestionStateResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisLearningDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryConnectionDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryWithRelationsDTO;
import de.tum.cit.aet.artemis.iris.exception.IrisException;
import de.tum.cit.aet.artemis.iris.exception.IrisForbiddenException;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisVariantDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisLearningDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryConnectionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryWithRelationsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.web.open.PublicPyrisStatusUpdateResource;

/**
 * This service connects to the Python implementation of Iris (called Pyris).
 * Pyris is responsible for executing the pipelines using (MM)LLMs and other tools asynchronously.
 * Status updates are sent to Artemis via {@link PublicPyrisStatusUpdateResource}
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
public class PyrisConnectorService {

    private static final Logger log = LoggerFactory.getLogger(PyrisConnectorService.class);

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private final PyrisJobService pyrisJobService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    @Value("${artemis.iris.url}")
    private String pyrisUrl;

    public PyrisConnectorService(@Qualifier("pyrisRestTemplate") RestTemplate restTemplate, MappingJackson2HttpMessageConverter springMvcJacksonConverter,
            PyrisJobService pyrisJobService) {
        this.restTemplate = restTemplate;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.pyrisJobService = pyrisJobService;
    }

    /**
     * Lists all Memiris memories for a user.
     *
     * @param userId the Artemis user id
     * @return list of memories (can be empty)
     */
    public List<MemirisMemoryDTO> listMemirisMemories(long userId) {
        try {
            var response = restTemplate.getForEntity(pyrisUrl + "/api/v1/memiris/user/" + userId, MemirisMemoryDTO[].class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody() || response.getBody() == null) {
                return List.of();
            }
            return Arrays.asList(response.getBody());
        }
        catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new EntityNotFoundException("Memiris resource not found");
            }
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to list Memiris memories for user {}", userId, e);
            throw new PyrisConnectorException("Could not fetch memories from Pyris");
        }
    }

    /**
     * Retrieves a specific Memiris memory with its relations (learnings and connections) for a user.
     *
     * @param userId   the Artemis user id
     * @param memoryId the memory id
     * @return flattened DTO with memory fields at top-level and relations attached
     */
    public MemirisMemoryWithRelationsDTO getMemirisMemoryWithRelations(long userId, String memoryId) {
        try {
            var response = restTemplate.getForEntity(pyrisUrl + "/api/v1/memiris/user/" + userId + "/" + memoryId, PyrisMemoryWithRelationsDTO.class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody() || response.getBody() == null) {
                throw new PyrisConnectorException("Could not fetch memory from Pyris");
            }
            PyrisMemoryWithRelationsDTO body = response.getBody();
            PyrisMemoryDTO m = body.memory();
            var learnings = body.learnings().stream().map(this::mapLearning).toList();
            var connections = body.connections().stream().map(this::mapConnection).toList();
            return new MemirisMemoryWithRelationsDTO(m.id(), m.title(), m.content(), m.sleptOn(), m.deleted(), learnings, connections);
        }
        catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new EntityNotFoundException("Memiris memory", memoryId);
            }
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to fetch Memiris memory {} for user {}", memoryId, userId, e);
            throw new PyrisConnectorException("Could not fetch memory from Pyris");
        }
    }

    private MemirisLearningDTO mapLearning(PyrisLearningDTO l) {
        return new MemirisLearningDTO(l.id(), l.title(), l.content(), l.reference(), l.memories());
    }

    private MemirisMemoryConnectionDTO mapConnection(PyrisMemoryConnectionDTO c) {
        var memoryIds = c.memories().stream().map(PyrisMemoryDTO::id).toList();
        return new MemirisMemoryConnectionDTO(c.id(), c.connectionType(), memoryIds, c.description(), c.weight());
    }

    /**
     * Deletes a specific Memiris memory for a user.
     *
     * @param userId   the Artemis user id
     * @param memoryId the memory id to delete
     */
    public void deleteMemirisMemory(long userId, String memoryId) {
        try {
            restTemplate.delete(pyrisUrl + "/api/v1/memiris/user/" + userId + "/" + memoryId);
        }
        catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new EntityNotFoundException("Memiris memory", memoryId);
            }
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to delete Memiris memory {} for user {}", memoryId, userId, e);
            throw new PyrisConnectorException("Could not delete memory in Pyris");
        }
    }

    /**
     * Requests all available variants from Pyris for a feature
     *
     * @param feature The feature to get the variants for
     * @return A list of available Models as IrisVariantDTO
     */
    public List<PyrisVariantDTO> getAvailableVariants(IrisSubSettingsType feature) throws PyrisConnectorException {
        try {
            var response = restTemplate.getForEntity(pyrisUrl + "/api/v1/pipelines/" + feature.name() + "/variants", PyrisVariantDTO[].class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                throw new PyrisConnectorException("Could not fetch offered variants");
            }
            return Arrays.asList(response.getBody());
        }
        catch (HttpStatusCodeException e) {
            log.error("Failed to fetch offered variants from Pyris", e);
            throw new PyrisConnectorException("Could not fetch offered variants");
        }
    }

    /**
     * Executes a pipeline with the given feature and variant
     *
     * @param feature      The feature name of the pipeline to execute
     * @param executionDTO The DTO sent as a body for the execution
     * @param event        The event to be sent as a query parameter, if the pipeline is getting executed due to an event
     */
    public void executePipeline(String feature, Object executionDTO, Optional<String> event) {
        var endpoint = "/api/v1/pipelines/" + feature + "/run";
        // Add event query parameter if present
        endpoint += event.map(e -> "?event=" + e).orElse("");
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
     * @param executionDTO The DTO sent as a body for the execution
     */
    public void executeLectureAdditionWebhook(PyrisWebhookLectureIngestionExecutionDTO executionDTO) {
        var endpoint = "/api/v1/webhooks/lectures/ingest";
        try {
            restTemplate.postForEntity(pyrisUrl + endpoint, objectMapper.valueToTree(executionDTO), Void.class);
        }
        catch (HttpStatusCodeException e) {
            log.error("Failed to send lecture unit {} to Pyris: {}", executionDTO.pyrisLectureUnit().lectureUnitId(), e.getMessage());
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send lecture unit {} to Pyris: {}", executionDTO.pyrisLectureUnit().lectureUnitId(), e.getMessage());
            throw new PyrisConnectorException("Could not fetch response from Pyris");
        }
    }

    /**
     * Retrieves the ingestion state of the lecture unit specified by retrieving the ingestion state from the vector database in Pyris.
     *
     * @param courseId      id of the course
     * @param lectureId     id of the lecture
     * @param lectureUnitId id of the lectureUnit to check in the Pyris vector database
     * @return The ingestion state of the lecture Unit
     *
     */
    IngestionState getLectureUnitIngestionState(long courseId, long lectureId, long lectureUnitId) {
        try {
            String encodedBaseUrl = URLEncoder.encode(artemisBaseUrl, StandardCharsets.UTF_8);
            String url = pyrisUrl + "/api/v1/courses/" + courseId + "/lectures/" + lectureId + "/lectureUnits/" + lectureUnitId + "/ingestion-state?base_url=" + encodedBaseUrl;
            IngestionStateResponseDTO response = restTemplate.getForObject(url, IngestionStateResponseDTO.class);
            IngestionState state = response.state();
            if (state != IngestionState.DONE) {
                if (pyrisJobService.currentJobs().stream().filter(job -> job instanceof LectureIngestionWebhookJob).map(job -> (LectureIngestionWebhookJob) job)
                        .anyMatch(ingestionJob -> ingestionJob.courseId() == courseId && ingestionJob.lectureId() == lectureId && ingestionJob.lectureUnitId() == lectureUnitId)) {
                    return IngestionState.IN_PROGRESS;
                }
            }
            return state;
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Error fetching ingestion state for lecture {}, lecture unit {}", lectureId, lectureUnitId, e);
            throw new PyrisConnectorException("Error fetching ingestion state for lecture unit" + lectureUnitId);
        }
    }

    /**
     * Executes a webhook and send lectures to the webhook with the given variant
     *
     * @param executionDTO The DTO sent as a body for the execution
     */
    public void executeLectureDeletionWebhook(PyrisWebhookLectureDeletionExecutionDTO executionDTO) {
        var endpoint = "/api/v1/webhooks/lectures/delete";
        try {
            restTemplate.postForEntity(pyrisUrl + endpoint, objectMapper.valueToTree(executionDTO), Void.class);
        }
        catch (HttpStatusCodeException e) {
            log.error("Failed to send lectures to Pyris", e);
            throw toIrisException(e);
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

    /**
     * Executes a webhook and send faqs to the webhook with the given variant. This webhook adds an FAQ in the Pyris system.
     *
     * @param toUpdateFaq  The DTO containing the faq to update
     * @param executionDTO The DTO sent as a body for the execution
     */
    public void executeFaqAdditionWebhook(PyrisFaqWebhookDTO toUpdateFaq, PyrisWebhookFaqIngestionExecutionDTO executionDTO) {
        var endpoint = "/api/v1/webhooks/faqs/ingest";
        try {
            restTemplate.postForEntity(pyrisUrl + endpoint, objectMapper.valueToTree(executionDTO), Void.class);
        }
        catch (HttpStatusCodeException e) {
            log.error("Failed to send faq {} to Pyris: {}", toUpdateFaq.faqId(), e.getMessage());
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send faq {} to Pyris: {}", toUpdateFaq.faqId(), e.getMessage());
            throw new PyrisConnectorException("Could not fetch response from Pyris");
        }
    }

    /**
     * Executes a webhook and adds faqs to the webhook with the given variant. This webhook deletes an FAQ in the Pyris system.
     *
     * @param executionDTO The DTO sent as a body for the execution
     */
    public void executeFaqDeletionWebhook(PyrisWebhookFaqDeletionExecutionDTO executionDTO) {
        var endpoint = "/api/v1/webhooks/faqs/delete";
        try {
            restTemplate.postForEntity(pyrisUrl + endpoint, objectMapper.valueToTree(executionDTO), Void.class);
        }
        catch (HttpStatusCodeException e) {
            log.error("Failed to send faqs to Pyris", e);
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send faqs to Pyris", e);
            throw new PyrisConnectorException("Could not fetch response from Pyris");
        }
    }

    /**
     * Retrieves the ingestion state of the faq specified by retrieving the ingestion state from the vector database in Pyris.
     *
     * @param courseId id of the course
     * @return The ingestion state of the faq
     *
     */
    IngestionState getFaqIngestionState(long courseId, long faqId) {
        try {
            String encodedBaseUrl = URLEncoder.encode(artemisBaseUrl, StandardCharsets.UTF_8);
            String url = pyrisUrl + "/api/v1/courses/" + courseId + "/faqs/" + faqId + "/ingestion-state?base_url=" + encodedBaseUrl;
            IngestionStateResponseDTO response = restTemplate.getForObject(url, IngestionStateResponseDTO.class);
            return response.state();
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Error fetching ingestion state for faq {}", faqId, e);
            throw new PyrisConnectorException("Error fetching ingestion state for faq" + faqId);
        }

    }
}
