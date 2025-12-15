package de.tum.cit.aet.artemis.nebula.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.lecture.api.LectureContentProcessingApi;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionInitResponseDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionStatusResponseDTO;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;

/**
 * Service for managing lecture transcription jobs.
 * Communicates with the external Nebula service to poll transcription job status and persist results.
 */
@Conditional(NebulaEnabled.class)
@Service
@Lazy
@Profile(PROFILE_CORE)
public class LectureTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(LectureTranscriptionService.class);

    private static final int MAX_FAILURES = 3;

    private final LectureTranscriptionsRepositoryApi lectureTranscriptionsRepositoryApi;

    private final LectureUnitRepositoryApi lectureUnitRepositoryApi;

    private final RestTemplate restTemplate;

    private final String nebulaBaseUrl;

    private final String nebulaSecretToken;

    private final LectureContentProcessingApi contentProcessingApi;

    private final ConcurrentHashMap<String, Integer> failureCountMap = new ConcurrentHashMap<>();

    public LectureTranscriptionService(LectureTranscriptionsRepositoryApi lectureTranscriptionsRepositoryApi, LectureUnitRepositoryApi lectureUnitRepositoryApi,
            @Qualifier("nebulaRestTemplate") RestTemplate restTemplate, @Value("${artemis.nebula.url}") String nebulaBaseUrl,
            @Value("${artemis.nebula.secret-token}") String nebulaSecretToken, @Lazy LectureContentProcessingApi contentProcessingApi) {
        this.lectureTranscriptionsRepositoryApi = lectureTranscriptionsRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.restTemplate = restTemplate;
        this.nebulaBaseUrl = nebulaBaseUrl;
        this.nebulaSecretToken = nebulaSecretToken;
        this.contentProcessingApi = contentProcessingApi;
    }

    /**
     * Polls the Nebula service for the current status of a transcription job.
     * If the transcription is completed, the result is saved.
     * If it failed, the job is marked as failed.
     *
     * @param transcription The LectureTranscription entity to process
     */
    public void processTranscription(LectureTranscription transcription) {
        String jobId = transcription.getJobId();
        if (jobId == null) {
            log.warn("Transcription has no jobId, skipping: {}", transcription.getId());
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", nebulaSecretToken);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            String url = nebulaBaseUrl + "/transcribe/status/" + jobId;
            ResponseEntity<NebulaTranscriptionStatusResponseDTO> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, NebulaTranscriptionStatusResponseDTO.class);
            NebulaTranscriptionStatusResponseDTO response = responseEntity.getBody();
            if (response == null) {
                log.warn("Nebula status response has no body for jobId={}", jobId);
                return;
            }

            // Clear failure counter on successful response
            failureCountMap.remove(jobId);

            if (response.isCompleted()) {
                if (transcription.getLectureUnit() == null) {
                    log.error("Transcription has no associated lecture unit for jobId={}", jobId);
                    markTranscriptionAsFailed(transcription, "No associated lecture unit");
                    return;
                }
                LectureTranscriptionDTO dto = response.toLectureTranscriptionDTO(transcription.getLectureUnit().getId());
                saveFinalTranscriptionResult(jobId, dto);
                log.info("Transcription completed and saved for jobId={}", jobId);
            }
            else if (response.hasFailed()) {
                markTranscriptionAsFailed(transcription, response.error());
            }
            else {
                log.debug("Transcription still in progress for jobId={}", jobId);
            }
        }
        catch (Exception e) {
            log.error("Error while polling transcription job {}: {}", jobId, e.getMessage(), e);

            int failures = failureCountMap.merge(jobId, 1, Integer::sum);

            if (failures >= MAX_FAILURES) {
                markTranscriptionAsFailed(transcription, "Failed after " + failures + " attempts: " + e.getMessage());
                failureCountMap.remove(jobId);
            }
        }
    }

    /**
     * Saves the final transcription result in the database once it has been marked as completed by Nebula.
     * Also notifies the content processing service to continue with ingestion.
     *
     * @param jobId The Nebula job ID
     * @param dto   The completed transcription result returned from Nebula
     */
    void saveFinalTranscriptionResult(String jobId, LectureTranscriptionDTO dto) {
        LectureTranscription transcription = lectureTranscriptionsRepositoryApi.findByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("No transcription found for jobId: " + jobId));

        transcription.setLanguage(dto.language());
        transcription.setSegments(dto.segments());
        transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);

        LectureTranscription savedTranscription = lectureTranscriptionsRepositoryApi.save(transcription);

        // Notify processing service to continue with ingestion
        contentProcessingApi.handleTranscriptionComplete(savedTranscription);
    }

    /**
     * Marks a transcription job as failed in the database with a given error message.
     * Also notifies the content processing service to handle the failure.
     *
     * @param transcription The transcription entity to update
     * @param errorMessage  The error message returned by Nebula
     */
    void markTranscriptionAsFailed(LectureTranscription transcription, String errorMessage) {
        transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);
        LectureTranscription savedTranscription = lectureTranscriptionsRepositoryApi.save(transcription);
        log.warn("Transcription failed for jobId={}, reason: {}", transcription.getJobId(), errorMessage);

        // Notify processing service to handle failure
        contentProcessingApi.handleTranscriptionComplete(savedTranscription);
    }

    /**
     * Creates a new empty LectureTranscription entry for a LectureUnit with status PENDING and the given job ID.
     * If a previous transcription for the unit exists, it is deleted first.
     *
     * @param lectureId     ID of the lecture
     * @param lectureUnitId ID of the lecture unit
     * @param jobId         The Nebula job ID for this transcription
     */
    void createEmptyTranscription(Long lectureId, Long lectureUnitId, String jobId) {
        LectureUnit lectureUnit = validateAndCleanup(lectureId, lectureUnitId);

        LectureTranscription transcription = new LectureTranscription();
        transcription.setLectureUnit(lectureUnit);
        transcription.setJobId(jobId);
        transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);

        lectureTranscriptionsRepositoryApi.save(transcription);
    }

    /**
     * Validates the lecture/lecture unit relationship and deletes any existing transcription for the given lecture unit.
     *
     * @param lectureId     ID of the lecture
     * @param lectureUnitId ID of the lecture unit
     * @return The validated LectureUnit entity
     * @throws ResponseStatusException if the unit does not belong to the lecture
     */
    private LectureUnit validateAndCleanup(Long lectureId, Long lectureUnitId) {
        LectureUnit lectureUnit = lectureUnitRepositoryApi.findByIdElseThrow(lectureUnitId);

        if (lectureUnit.getLecture() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lecture Unit has no associated Lecture.");
        }

        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lecture Unit does not belong to the Lecture.");
        }

        lectureTranscriptionsRepositoryApi.findByLectureUnit_Id(lectureUnitId).ifPresent(existing -> {
            lectureTranscriptionsRepositoryApi.deleteById(existing.getId());
            lectureTranscriptionsRepositoryApi.flush();
        });

        return lectureUnit;
    }

    /**
     * Initiates a new transcription job with the Nebula service and creates a placeholder transcription entry.
     *
     * @param lectureId     ID of the lecture
     * @param lectureUnitId ID of the lecture unit
     * @param request       The transcription request containing video URL and other parameters
     * @return The job ID returned by Nebula
     * @throws ResponseStatusException if the request fails or Nebula returns an invalid response
     */
    public String startNebulaTranscription(Long lectureId, Long lectureUnitId, NebulaTranscriptionRequestDTO request) {
        try {
            log.info("Starting transcription for Lecture ID {}, Unit ID {}", lectureId, lectureUnitId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", nebulaSecretToken);
            HttpEntity<NebulaTranscriptionRequestDTO> entity = new HttpEntity<>(request, headers);

            String url = nebulaBaseUrl + "/transcribe/start";
            ResponseEntity<NebulaTranscriptionInitResponseDTO> responseEntity = restTemplate.exchange(url, HttpMethod.POST, entity, NebulaTranscriptionInitResponseDTO.class);
            NebulaTranscriptionInitResponseDTO response = responseEntity.getBody();

            // Validate response
            if (response == null) {
                log.error("Nebula returned null response body for Lecture ID {}, Unit ID {}", lectureId, lectureUnitId);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nebula did not return a response body");
            }

            if (response.jobId() == null) {
                log.error("Nebula returned null or missing transcription ID for Lecture ID {}, Unit ID {}", lectureId, lectureUnitId);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nebula did not return a valid transcription ID");
            }

            // Create placeholder transcription for async processing
            createEmptyTranscription(lectureId, lectureUnitId, response.jobId());

            log.info("Transcription started for Lecture ID {}, Unit ID {}, Job ID: {}", lectureId, lectureUnitId, response.jobId());
            return response.jobId();
        }
        catch (Exception e) {
            log.error("Error initiating transcription for Lecture ID: {}, Unit ID: {} → {}", lectureId, lectureUnitId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start transcription: " + e.getMessage(), e);
        }
    }

    /**
     * Cancels a transcription job with the Nebula service and permanently deletes the transcription record.
     *
     * @param lectureUnitId The lecture unit ID to cancel transcription for
     * @throws ResponseStatusException if the job is not found, cancellation fails, or Nebula returns an invalid response
     */
    public void cancelNebulaTranscription(Long lectureUnitId) {
        LectureTranscription transcription = lectureTranscriptionsRepositoryApi.findByLectureUnit_Id(lectureUnitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No transcription found for lectureUnitId: " + lectureUnitId));

        String jobId = transcription.getJobId();
        if (jobId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transcription has no job ID");
        }

        // Only cancel if transcription is still pending or processing
        if (transcription.getTranscriptionStatus() == TranscriptionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel a completed transcription");
        }

        if (transcription.getTranscriptionStatus() == TranscriptionStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel a failed transcription");
        }

        try {
            log.info("Cancelling transcription for lectureUnitId={}, jobId={}", lectureUnitId, jobId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", nebulaSecretToken);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            String url = nebulaBaseUrl + "/transcribe/cancel/" + jobId;
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);

            // Only delete if cancellation was successful
            if (response.getStatusCode().is2xxSuccessful()) {
                lectureTranscriptionsRepositoryApi.deleteById(transcription.getId());
                lectureTranscriptionsRepositoryApi.flush();
                log.info("Transcription cancelled and deleted successfully for lectureUnitId={}, jobId={}", lectureUnitId, jobId);
            }
            else {
                log.error("Nebula cancellation failed for lectureUnitId={}, jobId={}, status={}", lectureUnitId, jobId, response.getStatusCode());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nebula cancellation returned status: " + response.getStatusCode());
            }
        }
        catch (Exception e) {
            if (e instanceof ResponseStatusException) {
                throw (ResponseStatusException) e;
            }
            log.error("Error cancelling transcription for lectureUnitId: {}, jobId: {} → {}", lectureUnitId, jobId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to cancel transcription: " + e.getMessage(), e);
        }
    }
}
