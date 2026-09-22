package de.tum.cit.aet.artemis.iris.service.pyris;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSupportLevel;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;
import de.tum.cit.aet.artemis.iris.dto.IngestionStateResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisLearningDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryConnectionDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDataDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryWithRelationsDTO;
import de.tum.cit.aet.artemis.iris.exception.IrisException;
import de.tum.cit.aet.artemis.iris.exception.IrisForbiddenException;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitMetadataWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitVisibilityWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisLearningDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryConnectionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryWithRelationsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource;

/**
 * This service connects to the Python implementation of Iris (called Pyris).
 * Pyris is responsible for executing the pipelines using (MM)LLMs and other tools asynchronously.
 * Status updates are sent to Artemis via {@link PyrisInternalStatusUpdateResource}
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisConnectorService {

    /**
     * What Pyris puts in the body when it is asked about a lecture unit it has not ingested.
     */
    private static final String LECTURE_UNIT_NOT_INGESTED_DETAIL = "Lecture unit has not been ingested";

    private static final Logger log = LoggerFactory.getLogger(PyrisConnectorService.class);

    /**
     * A Memiris memory id as it may appear in a Pyris URL: one opaque path segment, no separators and no dot segments.
     */
    private static final Pattern MEMIRIS_MEMORY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final RestTemplate restTemplate;

    private final JsonMapper objectMapper;

    @Value("${server.url}")
    private String artemisBaseUrl;

    @Value("${artemis.iris.url}")
    private String pyrisUrl;

    public PyrisConnectorService(@Qualifier("pyrisRestTemplate") RestTemplate restTemplate, JsonMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all Memiris memories for a user.
     *
     * @param userId the Artemis user id
     * @return list of memories (can be empty)
     */
    public MemirisMemoryDataDTO listMemirisMemoryData(long userId) {
        try {
            var response = restTemplate.getForEntity(pyrisUrl + "/api/v2/memiris/user/" + userId, MemirisMemoryDataDTO.class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody() || response.getBody() == null) {
                return new MemirisMemoryDataDTO(List.of(), List.of(), List.of());
            }
            return response.getBody();
        }
        catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new EntityNotFoundException("Memiris resource not found");
            }
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to list Memiris memory data for user {}", userId, e);
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
        validateMemoryId(memoryId);
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

    /**
     * Rejects a Memiris memory id that is not a single opaque path segment.
     *
     * <p>
     * The id arrives as a {@code @PathVariable} from any signed-in student and is interpolated into the Pyris URL, so
     * what it may contain is a security boundary rather than a formatting concern. The user id in front of it is the
     * only thing scoping a request to its own memories, and a {@code ../} inside the id walks straight past it:
     * {@code .../memiris/user/42/../../99/some-memory} normalizes to another user's memory before the request is even
     * sent. Percent-encoding alone does not close this, because a literal {@code ..} is an unreserved path segment and
     * survives encoding intact - so the id is validated rather than escaped.
     *
     * <p>
     * Memiris ids are opaque tokens (Weaviate UUIDs in production), which the accepted character set covers.
     *
     * @param memoryId the memory id to validate
     * @throws BadRequestAlertException if the id could address anything other than a single memory
     */
    private static void validateMemoryId(String memoryId) {
        if (memoryId == null || !MEMIRIS_MEMORY_ID_PATTERN.matcher(memoryId).matches()) {
            throw new BadRequestAlertException("Invalid Memiris memory id", "memiris", "invalidMemoryId");
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
        validateMemoryId(memoryId);
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
     * Deletes all Memiris memories for a user.
     *
     * @param userId the Artemis user id
     */
    public void deleteAllMemirisMemories(long userId) {
        try {
            restTemplate.delete(pyrisUrl + "/api/v1/memiris/user/" + userId + "/delete-all");
        }
        catch (HttpStatusCodeException e) {
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to delete all Memiris memories for user {}", userId, e);
            throw new PyrisConnectorException("Could not delete all memories in Pyris");
        }
    }

    /**
     * Searches for lecture units in Pyris using a query string.
     *
     * @param query            the search query
     * @param limit            the maximum number of results to return
     * @param courseIds        optional list of course IDs to restrict the search scope; null means global search across all courses
     * @param excludeCourseIds optional list of course IDs Pyris has to hide itself; only needed for a caller sent without a course ceiling, since every other
     *                             exclusion is already subtracted from {@code courseIds}
     * @param accessContext    the requesting user's role-grouped course access, applied by Pyris as an opaque filter; null for old clients
     * @return list of matching lecture search results
     */
    public List<PyrisLectureSearchResultDTO> searchLectures(String query, int limit, @Nullable List<Long> courseIds, @Nullable List<Long> excludeCourseIds,
            @Nullable PyrisAccessContextDTO accessContext) {
        var endpoint = "/api/v1/search/lectures";
        try {
            var requestDTO = new PyrisLectureSearchRequestDTO(query, limit, courseIds, excludeCourseIds, accessContext);
            var response = restTemplate.postForEntity(pyrisUrl + endpoint, requestDTO, PyrisLectureSearchResultDTO[].class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody() || response.getBody() == null) {
                return List.of();
            }
            return Arrays.asList(response.getBody());
        }
        catch (HttpStatusCodeException e) {
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to search lectures in Pyris", e);
            throw new PyrisConnectorException("Could not fetch lecture search results from Pyris");
        }
    }

    /**
     * Asks Pyris to answer a question using course content via the async pipeline (POST /api/v1/pipelines/global-search/run → 202).
     * Pyris will POST two webhook callbacks to Artemis:
     * 1. A "thinking" update (~2 ms after this call) when the query is classified as a real question.
     * 2. A "result" update when the LLM finishes, containing the answer (or null for navigation queries).
     *
     * @param query         the user's question
     * @param limit         the maximum number of source segments to retrieve
     * @param jobToken      the Hazelcast job token used for callback authentication and WebSocket routing
     * @param aiSelection   the user's LLM selection (LOCAL_AI or CLOUD_AI)
     * @param accessContext the requesting user's role-grouped course access, applied by Pyris as an opaque filter (may be null)
     */
    public void executeGlobalSearchIrisAnswer(String query, int limit, String jobToken, AiSelectionDecision aiSelection, @Nullable PyrisAccessContextDTO accessContext) {
        var endpoint = "/api/v1/pipelines/global-search/run";
        try {
            var settings = new PyrisPipelineExecutionSettingsDTO(jobToken, aiSelection, artemisBaseUrl, null, IrisSupportLevel.MODERATE.jsonValue());
            var requestDTO = new PyrisGlobalSearchAnswerRequestDTO(query, limit, settings, accessContext);
            var response = restTemplate.postForEntity(pyrisUrl + endpoint, requestDTO, Void.class);
            if (response.getStatusCode().value() != HttpStatus.ACCEPTED.value()) {
                log.warn("Unexpected status {} from Pyris search/ask async", response.getStatusCode().value());
                throw new PyrisConnectorException("Unexpected status from Pyris global-search pipeline: " + response.getStatusCode().value());
            }
        }
        catch (HttpStatusCodeException e) {
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send async search/ask request to Pyris", e);
            throw new PyrisConnectorException("Could not send global search Iris answer request to Pyris");
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
            restTemplate.postForEntity(pyrisUrl + endpoint, executionDTO, Void.class);
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
            restTemplate.postForEntity(pyrisUrl + endpoint, executionDTO, Void.class);
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
     * Executes a lightweight lecture metadata webhook in Pyris.
     *
     * @param dto The DTO sent as a body for the execution
     * @return whether Pyris accepted the update, false if it does not hold the lecture unit
     */
    public boolean executeLectureMetadataWebhook(PyrisLectureUnitMetadataWebhookDTO dto) {
        var endpoint = "/api/v1/webhooks/lectures/metadata";
        try {
            restTemplate.postForEntity(pyrisUrl + endpoint, dto, Void.class);
            return true;
        }
        catch (HttpStatusCodeException e) {
            if (reportsLectureUnitNotIngested(e)) {
                // See executeLectureVisibilityWebhook: a unit Pyris never ingested has no metadata to update either.
                log.info("Pyris does not hold lecture unit {}, so its metadata has nothing to update", dto.lectureUnitId());
                return false;
            }
            log.error("Failed to send lecture unit metadata {} to Pyris: {}", dto.lectureUnitId(), e.getMessage());
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send lecture unit metadata {} to Pyris: {}", dto.lectureUnitId(), e.getMessage());
            throw new PyrisConnectorException("Could not send lecture metadata to Pyris");
        }
    }

    /**
     * Executes a lightweight lecture visibility webhook in Pyris.
     *
     * @param dto The DTO sent as a body for the execution
     * @return whether Pyris accepted the update, false if it does not hold the lecture unit
     */
    public boolean executeLectureVisibilityWebhook(PyrisLectureUnitVisibilityWebhookDTO dto) {
        var endpoint = "/api/v1/webhooks/lectures/visibility";
        try {
            restTemplate.postForEntity(pyrisUrl + endpoint, dto, Void.class);
            return true;
        }
        catch (HttpStatusCodeException e) {
            if (reportsLectureUnitNotIngested(e)) {
                // Pyris answers 404 for a unit it never ingested. There is no visibility to update, and a retry cannot
                // create one, so this is reported back as an outcome rather than raised as a failure.
                log.info("Pyris does not hold lecture unit {}, so its visibility has nothing to update", dto.lectureUnitId());
                return false;
            }
            log.error("Failed to send lecture unit visibility {} to Pyris: {}", dto.lectureUnitId(), e.getMessage());
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send lecture unit visibility {} to Pyris: {}", dto.lectureUnitId(), e.getMessage());
            throw new PyrisConnectorException("Could not send lecture visibility to Pyris");
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
            restTemplate.postForEntity(pyrisUrl + endpoint, executionDTO, Void.class);
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

    /**
     * @param exception the error Pyris answered a lecture unit webhook with
     * @return whether it is Pyris reporting that it does not hold the lecture unit, rather than any other 404
     */
    private static boolean reportsLectureUnitNotIngested(HttpStatusCodeException exception) {
        if (exception.getStatusCode() != HttpStatus.NOT_FOUND) {
            return false;
        }
        // The status alone is not enough. A renamed endpoint or a gateway in front of Pyris also answers 404, and
        // reading that as "this unit was never ingested" would settle every lecture unit of the installation at once.
        return exception.getResponseBodyAsString().contains(LECTURE_UNIT_NOT_INGESTED_DETAIL);
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
            return objectMapper.readTree(ex.getResponseBodyAsString()).required("detail").required("errorMessage").asString();
        }
        catch (JacksonException | IllegalArgumentException e) {
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
            restTemplate.postForEntity(pyrisUrl + endpoint, executionDTO, Void.class);
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
            restTemplate.postForEntity(pyrisUrl + endpoint, executionDTO, Void.class);
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
