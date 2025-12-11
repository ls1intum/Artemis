package de.tum.cit.aet.artemis.nebula.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.service.LectureTranscriptionService;

/**
 * API for lecture transcription operations.
 * This class allows other modules to interact with the transcription service.
 */
@Conditional(NebulaEnabled.class)
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class LectureTranscriptionApi extends AbstractNebulaApi {

    private final LectureTranscriptionService lectureTranscriptionService;

    public LectureTranscriptionApi(LectureTranscriptionService lectureTranscriptionService) {
        this.lectureTranscriptionService = lectureTranscriptionService;
    }

    /**
     * Initiates a new transcription job with the Nebula service.
     *
     * @param lectureId     ID of the lecture
     * @param lectureUnitId ID of the lecture unit
     * @param request       The transcription request containing video URL and other parameters
     * @return The job ID returned by Nebula
     */
    public String startNebulaTranscription(Long lectureId, Long lectureUnitId, NebulaTranscriptionRequestDTO request) {
        return lectureTranscriptionService.startNebulaTranscription(lectureId, lectureUnitId, request);
    }
}
