package de.tum.cit.aet.artemis.nebula.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.iris.api.IrisTranscriptionApi;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;

/**
 * Service for managing lecture transcription jobs.
 * Initiates transcription jobs with the Pyris service via webhook and persists the resulting transcription data.
 */
@Conditional(NebulaEnabled.class)
@Service
@Lazy
public class LectureTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(LectureTranscriptionService.class);

    private final LectureTranscriptionsRepositoryApi lectureTranscriptionsRepositoryApi;

    private final LectureUnitRepositoryApi lectureUnitRepositoryApi;

    private final RestTemplate restTemplate;

    private final Optional<RestTemplate> pyrisRestTemplate;

    private final String nebulaBaseUrl;

    private final String nebulaSecretToken;

    private final String irisBaseUrl;

    private final Optional<IrisTranscriptionApi> irisTranscriptionApi;

    private final String artemisBaseUrl;

    public LectureTranscriptionService(LectureTranscriptionsRepositoryApi lectureTranscriptionsRepositoryApi, LectureUnitRepositoryApi lectureUnitRepositoryApi,
            @Qualifier("nebulaRestTemplate") RestTemplate restTemplate, @Qualifier("pyrisRestTemplate") Optional<RestTemplate> pyrisRestTemplate,
            @Value("${artemis.nebula.url}") String nebulaBaseUrl, @Value("${artemis.nebula.secret-token}") String nebulaSecretToken,
            Optional<IrisTranscriptionApi> irisTranscriptionApi, @Value("${server.url}") String artemisBaseUrl, @Value("${artemis.iris.url}") String irisBaseUrl) {
        this.lectureTranscriptionsRepositoryApi = lectureTranscriptionsRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.restTemplate = restTemplate;
        this.pyrisRestTemplate = pyrisRestTemplate;
        this.nebulaBaseUrl = nebulaBaseUrl;
        this.nebulaSecretToken = nebulaSecretToken;
        this.irisTranscriptionApi = irisTranscriptionApi;
        this.artemisBaseUrl = artemisBaseUrl;
        this.irisBaseUrl = irisBaseUrl;
    }

    /**
     * Creates a new empty LectureTranscription entry for a LectureUnit with status PENDING and the given job ID.
     * If a previous transcription for the unit exists, it is deleted first.
     *
     * @param lectureId     ID of the lecture
     * @param lectureUnitId ID of the lecture unit
     * @param jobId         The job token used for callback authentication
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
     * Initiates a new transcription job with the Nebula/Pyris service and creates a placeholder transcription entry.
     *
     * @param lectureId     ID of the lecture
     * @param lectureUnitId ID of the lecture unit
     * @param request       The transcription request containing video URL and other parameters
     * @return The job ID (authentication token) for the transcription
     * @throws ResponseStatusException if the request fails
     */
    public String startNebulaTranscription(Long lectureId, Long lectureUnitId, NebulaTranscriptionRequestDTO request) {
        try {
            log.info("Starting transcription for Lecture ID {}, Unit ID {}", lectureId, lectureUnitId);

            // Generate job token for callback authentication
            String jobToken = irisTranscriptionApi.map(api -> api.createTranscriptionJob(request.courseId(), lectureId, lectureUnitId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Iris transcription API not available"));

            // Build request with settings for callback
            NebulaTranscriptionRequestDTO.NebulaTranscriptionSettingsDTO settings = new NebulaTranscriptionRequestDTO.NebulaTranscriptionSettingsDTO(jobToken, artemisBaseUrl);

            NebulaTranscriptionRequestDTO fullRequest = new NebulaTranscriptionRequestDTO(request.videoUrl(), request.lectureUnitId(), request.lectureId(), request.courseId(),
                    request.courseName(), request.lectureName(), request.lectureUnitName(), request.videoSourceType(), settings);

            // Use pyrisRestTemplate for Pyris webhook endpoint (has correct auth interceptor)
            RestTemplate templateToUse = pyrisRestTemplate.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pyris REST template not available"));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            // Authorization header is automatically added by PyrisAuthorizationInterceptor
            HttpEntity<NebulaTranscriptionRequestDTO> entity = new HttpEntity<>(fullRequest, headers);

            // Create placeholder transcription before firing the webhook so any fast
            // callback from Pyris always finds an existing DB row.
            createEmptyTranscription(lectureId, lectureUnitId, jobToken);

            String url = irisBaseUrl + "/api/v1/webhooks/transcription/video";
            templateToUse.exchange(url, HttpMethod.POST, entity, Void.class);

            log.info("Transcription started for Lecture ID {}, Unit ID {}, Job ID: {}", lectureId, lectureUnitId, jobToken);
            return jobToken;
        }
        catch (ResponseStatusException e) {
            throw e;
        }
        catch (Exception e) {
            log.error("Error initiating transcription for Lecture ID: {}, Unit ID: {} → {}", lectureId, lectureUnitId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start transcription: " + e.getMessage(), e);
        }
    }

    /**
     * Cancels a transcription job with the Nebula service and permanently deletes the transcription record.
     * Does nothing if no transcription exists for the lecture unit.
     *
     * @param lectureUnitId The lecture unit ID to cancel transcription for
     * @throws ResponseStatusException if cancellation fails or Nebula returns an invalid response
     */
    public void cancelNebulaTranscription(Long lectureUnitId) {
        Optional<LectureTranscription> transcriptionOpt = lectureTranscriptionsRepositoryApi.findByLectureUnit_Id(lectureUnitId);
        if (transcriptionOpt.isEmpty()) {
            log.debug("No transcription found for lectureUnitId: {}, nothing to cancel", lectureUnitId);
            return;
        }
        LectureTranscription transcription = transcriptionOpt.get();

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
