package de.tum.cit.aet.artemis.lecture.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Service
public class LectureTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(LectureTranscriptionService.class);

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    public LectureTranscriptionService(LectureTranscriptionRepository lectureTranscriptionRepository, LectureUnitRepository lectureUnitRepository,
            RestClient.Builder restClientBuilder, ObjectMapper objectMapper, @Value("${artemis.nebula.base-url}") String nebulaBaseUrl,
            @Value("${artemis.nebula.secret-token}") String nebulaSecretToken) {

        this.lectureTranscriptionRepository = lectureTranscriptionRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.restClient = restClientBuilder.baseUrl(nebulaBaseUrl).defaultHeader("Authorization", nebulaSecretToken).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Saves the final result of a transcription job after Nebula reports completion.
     * Updates language, segments, and marks status as COMPLETED.
     */
    @Transactional
    public LectureTranscription saveFinalTranscriptionResult(String jobId, LectureTranscriptionDTO dto) {
        LectureTranscription transcription = lectureTranscriptionRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("No transcription found for jobId: " + jobId));

        transcription.setLanguage(dto.language());
        transcription.setSegments(dto.segments());
        transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);

        return lectureTranscriptionRepository.save(transcription);
    }

    /**
     * Creates a placeholder transcription in PENDING state, associated with a lecture unit.
     * Deletes any existing transcription for the same unit before saving.
     */
    @Transactional
    public void createEmptyTranscription(Long lectureId, Long lectureUnitId, String jobId) {
        LectureUnit lectureUnit = validateAndCleanup(lectureId, lectureUnitId);

        LectureTranscription t = new LectureTranscription();
        t.setLectureUnit(lectureUnit);
        t.setJobId(jobId);
        t.setTranscriptionStatus(TranscriptionStatus.PENDING);

        lectureTranscriptionRepository.save(t);
    }

    /**
     * Polls the Nebula API for the status of a transcription job and processes the result.
     * - If "done", saves the final result
     * - If "error", marks as FAILED
     * - If still processing, logs the current state
     */
    @Transactional
    public void processTranscriptionInTransaction(LectureTranscription transcription) {
        try {
            String jobId = transcription.getJobId();
            Map<String, Object> response = restClient.get().uri("/transcribe/status/" + jobId).retrieve().body(new ParameterizedTypeReference<>() {
            });

            String status = (String) response.get("status");

            if ("done".equals(status)) {
                LectureTranscriptionDTO dto = objectMapper.convertValue(response, LectureTranscriptionDTO.class);
                saveFinalTranscriptionResult(jobId, dto);
                log.info("Transcription completed and saved for jobId={}", jobId);
            }
            else if ("error".equals(status)) {
                transcription.setTranscriptionStatus(TranscriptionStatus.FAILED);
                lectureTranscriptionRepository.save(transcription);
                log.warn("Transcription failed for jobId={}, reason: {}", jobId, response.get("error"));
            }
            else {
                log.info("Transcription still in progress for jobId={}", jobId);
            }
        }
        catch (Exception e) {
            log.error("Error while polling transcription job {}: {}", transcription.getJobId(), e.getMessage(), e);
        }
    }

    /**
     * Verifies that the lecture unit belongs to the given lecture.
     * If an existing transcription exists for the unit, it is deleted immediately.
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
}
