package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionInitResponseDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionStatusResponseDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
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

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final RestClient restClient;

    public LectureTranscriptionService(LectureTranscriptionRepository lectureTranscriptionRepository, LectureUnitRepository lectureUnitRepository,
            RestClient.Builder restClientBuilder, @Value("${artemis.nebula.url}") String nebulaBaseUrl, @Value("${artemis.nebula.secret}") String nebulaSecretToken) {
        this.lectureTranscriptionRepository = lectureTranscriptionRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.restClient = restClientBuilder.baseUrl(nebulaBaseUrl).defaultHeader("Authorization", nebulaSecretToken).build();
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
        try {
            NebulaTranscriptionStatusResponseDTO response = restClient.get().uri("/transcribe/status/" + jobId).retrieve().body(NebulaTranscriptionStatusResponseDTO.class);

            if (response.isCompleted()) {
                LectureTranscriptionDTO dto = response.toLectureTranscriptionDTO(transcription.getLectureUnit().getId());
                saveFinalTranscriptionResult(jobId, dto);
                log.info("Transcription completed and saved for jobId={}", jobId);
            }
            else if (response.hasFailed()) {
                markTranscriptionAsFailed(transcription, response.error());
            }
            else {
                log.info("Transcription still in progress for jobId={}", jobId);
            }
        }
        catch (Exception e) {
            log.error("Error while polling transcription job {}: {}", jobId, e.getMessage(), e);
        }
    }

    /**
     * Saves the final transcription result in the database once it has been marked as completed by Nebula.
     *
     * @param jobId The Nebula job ID
     * @param dto   The completed transcription result returned from Nebula
     */
    public void saveFinalTranscriptionResult(String jobId, LectureTranscriptionDTO dto) {
        LectureTranscription transcription = lectureTranscriptionRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("No transcription found for jobId: " + jobId));

        transcription.setLanguage(dto.language());
        transcription.setSegments(dto.segments());
        transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);

        lectureTranscriptionRepository.save(transcription);
    }

    /**
     * Marks a transcription job as failed in the database with a given error message.
     *
     * @param transcription The transcription entity to update
     * @param errorMessage  The error message returned by Nebula
     */
    public void markTranscriptionAsFailed(LectureTranscription transcription, String errorMessage) {
        transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);
        lectureTranscriptionRepository.save(transcription);
        log.warn("Transcription failed for jobId={}, reason: {}", transcription.getJobId(), errorMessage);
    }

    /**
     * Creates a new empty LectureTranscription entry for a LectureUnit with status PENDING and the given job ID.
     * If a previous transcription for the unit exists, it is deleted first.
     *
     * @param lectureId     ID of the lecture
     * @param lectureUnitId ID of the lecture unit
     * @param jobId         The Nebula job ID for this transcription
     */
    public void createEmptyTranscription(Long lectureId, Long lectureUnitId, String jobId) {
        LectureUnit lectureUnit = validateAndCleanup(lectureId, lectureUnitId);

        LectureTranscription t = new LectureTranscription();
        t.setLectureUnit(lectureUnit);
        t.setJobId(jobId);
        t.setTranscriptionStatus(TranscriptionStatus.PENDING);

        lectureTranscriptionRepository.save(t);
    }

    /**
     * Validates the lecture/lecture unit relationship and deletes any existing transcription for the given lecture unit.
     *
     * @param lectureId     ID of the lecture
     * @param lectureUnitId ID of the lecture unit
     * @return The validated LectureUnit entity
     * @throws IllegalArgumentException if the unit does not belong to the lecture
     */
    private LectureUnit validateAndCleanup(Long lectureId, Long lectureUnitId) {
        LectureUnit lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);

        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new IllegalArgumentException("Lecture Unit does not belong to the Lecture.");
        }

        lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(existing -> {
            lectureTranscriptionRepository.deleteById(existing.getId());
            lectureTranscriptionRepository.flush();
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

            NebulaTranscriptionInitResponseDTO response = restClient.post().uri("/transcribe/start").header("Content-Type", "application/json").body(request).retrieve()
                    .body(NebulaTranscriptionInitResponseDTO.class);

            // Validate response
            if (response == null || response.transcriptionId() == null) {
                log.error("Nebula returned null or missing transcription ID for Lecture ID {}, Unit ID {}", lectureId, lectureUnitId);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nebula did not return a valid transcription ID");
            }

            // Create placeholder transcription for async processing
            createEmptyTranscription(lectureId, lectureUnitId, response.transcriptionId());

            log.info("Transcription started for Lecture ID {}, Unit ID {}, Job ID: {}", lectureId, lectureUnitId, response.transcriptionId());
            return response.transcriptionId();
        }
        catch (ResponseStatusException e) {
            // Re-throw our own exceptions
            throw e;
        }
        catch (Exception e) {
            log.error("Error initiating transcription for Lecture ID: {}, Unit ID: {} → {}", lectureId, lectureUnitId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start transcription: " + e.getMessage(), e);
        }
    }
}
