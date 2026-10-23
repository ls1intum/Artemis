package de.tum.cit.aet.artemis.iris.service.pyris;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.communication.dto.FaqCensusDTO;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSupportLevel;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;
import de.tum.cit.aet.artemis.iris.dto.IngestionStateResponseDTO;
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
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisEntityCandidateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusDTO;

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

    private final RestTemplate restTemplate;

    private final RestTemplate censusRestTemplate;

    private final JsonMapper objectMapper;

    @Value("${server.url}")
    private String artemisBaseUrl;

    @Value("${artemis.iris.url}")
    private String pyrisUrl;

    public PyrisConnectorService(@Qualifier("pyrisRestTemplate") RestTemplate restTemplate, @Qualifier("censusPyrisRestTemplate") RestTemplate censusRestTemplate,
            JsonMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.censusRestTemplate = censusRestTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch the per-course ingestion census from Pyris: the aggregated vector index state of every
     * lecture unit of the course, including the stamped content fingerprints.
     * <p>
     * The census is a read-only capability introduced together with the fingerprint stamping. An older
     * Pyris without the endpoint answers 404; that case and every transport failure return {@code null}
     * so callers treat the census as unavailable instead of failing their reconcile pass.
     *
     * @param courseId the id of the course to take the census for
     * @return the census, or {@code null} when Pyris does not offer or cannot answer the endpoint
     */
    @Nullable
    public IngestionCensusDTO getIngestionCensus(long courseId) {
        try {
            // Built as a URI and passed as one, so the base URL is encoded exactly once: the String overload of getForEntity
            // treats its argument as a template and encodes an already encoded value a second time.
            URI url = UriComponentsBuilder.fromUriString(pyrisUrl).path("/api/v1/courses/{courseId}/ingestion-census").queryParam("base_url", "{baseUrl}").encode()
                    .buildAndExpand(courseId, artemisBaseUrl).toUri();
            var response = censusRestTemplate.getForEntity(url, IngestionCensusDTO.class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                log.warn("Ingestion census for course {} returned status {} without a usable body", courseId, response.getStatusCode());
                return null;
            }
            return response.getBody();
        }
        catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("Pyris does not offer the ingestion census endpoint (404), skipping census for course {}", courseId);
            }
            else {
                log.warn("Ingestion census for course {} failed with status {}", courseId, e.getStatusCode());
            }
            return null;
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.warn("Ingestion census for course {} failed: {}", courseId, e.getMessage());
            return null;
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
            var requestDTO = new PyrisLectureSearchRequestDTO(query, limit, artemisBaseUrl, courseIds, excludeCourseIds, accessContext);
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
     * @param query            the user's question
     * @param limit            the maximum number of source segments to retrieve
     * @param jobToken         the Hazelcast job token used for callback authentication and WebSocket routing
     * @param aiSelection      the user's LLM selection (LOCAL_AI or CLOUD_AI)
     * @param accessContext    the requesting user's role-grouped course access, applied by Pyris as an opaque filter (may be null)
     * @param entityCandidates pre-fetched, access-filtered entity candidates for the answer pipeline (may be null or empty)
     * @param courseIds        optional course scope from the search UI's active course filter, {@code null} for unscoped
     *                             (search everything the access context permits)
     * @param excludeCourseIds course ids to hide regardless of {@code courseIds}; only needed for a caller with no
     *                             {@code courseIds} ceiling to narrow itself (unrestricted access)
     * @param searchesNothing  whether the caller already resolved the scope to nothing (e.g. every requested course
     *                             was excluded); distinct from an unscoped {@code courseIds}, and passed as its own
     *                             field since an empty {@code courseIds} list does not survive the wire
     */
    public void executeGlobalSearchIrisAnswer(String query, int limit, String jobToken, AiSelectionDecision aiSelection, @Nullable PyrisAccessContextDTO accessContext,
            @Nullable List<PyrisEntityCandidateDTO> entityCandidates, @Nullable List<Long> courseIds, @Nullable List<Long> excludeCourseIds, boolean searchesNothing) {
        var endpoint = "/api/v1/pipelines/global-search/run";
        try {
            // streamResponse: Pyris posts throttled partial-answer snapshots while the LLM generates,
            // which this service forwards to the client as partial WebSocket updates.
            var settings = new PyrisPipelineExecutionSettingsDTO(jobToken, aiSelection, artemisBaseUrl, null, IrisSupportLevel.MODERATE.jsonValue(), Boolean.TRUE);
            var requestDTO = new PyrisGlobalSearchAnswerRequestDTO(query, limit, settings, accessContext, entityCandidates, courseIds, excludeCourseIds, searchesNothing);
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
     * Executes a lightweight lecture metadata webhook in Pyris.
     *
     * @param dto The DTO sent as a body for the execution
     * @return whether Pyris accepted the update, false if it does not hold the lecture unit
     */
    public boolean executeLectureMetadataWebhook(PyrisLectureUnitMetadataWebhookDTO dto) {
        return executeLectureUpdateWebhook("metadata", dto.lectureUnitId(), dto);
    }

    /**
     * Executes a lightweight lecture visibility webhook in Pyris.
     *
     * @param dto The DTO sent as a body for the execution
     * @return whether Pyris accepted the update, false if it does not hold the lecture unit
     */
    public boolean executeLectureVisibilityWebhook(PyrisLectureUnitVisibilityWebhookDTO dto) {
        return executeLectureUpdateWebhook("visibility", dto.lectureUnitId(), dto);
    }

    /** Metadata and visibility updates have the same not-ingested outcome and transport failure semantics. */
    private boolean executeLectureUpdateWebhook(String operation, Long lectureUnitId, Object dto) {
        try {
            restTemplate.postForEntity(pyrisUrl + "/api/v1/webhooks/lectures/" + operation, dto, Void.class);
            return true;
        }
        catch (HttpStatusCodeException e) {
            if (reportsLectureUnitNotIngested(e)) {
                log.info("Pyris does not hold lecture unit {}, so its {} has nothing to update", lectureUnitId, operation);
                return false;
            }
            log.error("Failed to send lecture unit {} {} to Pyris: {}", operation, lectureUnitId, e.getMessage());
            throw toIrisException(e);
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.error("Failed to send lecture unit {} {} to Pyris: {}", operation, lectureUnitId, e.getMessage());
            throw new PyrisConnectorException("Could not send lecture " + operation + " to Pyris");
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
            // Pyris or a proxy in front of it can answer 400 or 500 with a body that is not the error JSON, e.g. a plain-text "Internal Server Error".
            // The caller turns the status into an IrisException, so a warning is enough.
            log.warn("Pyris answered with status {} and a body that is not the expected error JSON: {}", ex.getStatusCode(), e.getMessage());
            return "";
        }
    }

    /**
     * Fetch the FAQ census of this Artemis instance: every FAQ row the Iris index holds for it, plus legacy rows without an instance.
     *
     * @return the census, or {@code null} when it is unavailable (an older Pyris without the endpoint, an error, or a timeout); the caller then
     *         skips reconciliation
     */
    @Nullable
    public FaqCensusDTO getFaqCensus() {
        try {
            // Built as a URI and passed as one, so the base URL is encoded exactly once (see getIngestionCensus)
            URI url = UriComponentsBuilder.fromUriString(pyrisUrl).path("/api/v1/faq-census").queryParam("base_url", "{baseUrl}").encode().buildAndExpand(artemisBaseUrl).toUri();
            var response = censusRestTemplate.getForEntity(url, FaqCensusDTO.class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                log.warn("FAQ census returned status {} without a usable body", response.getStatusCode());
                return null;
            }
            return response.getBody();
        }
        catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("Pyris does not offer the FAQ census endpoint (404), skipping FAQ reconciliation");
            }
            else {
                log.warn("FAQ census failed with status {}", e.getStatusCode());
            }
            return null;
        }
        catch (RestClientException | IllegalArgumentException e) {
            log.warn("FAQ census failed: {}", e.getMessage());
            return null;
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
