package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;

/**
 * DTO representing the processing status of a lecture unit.
 * Used to show processing progress and errors in the UI.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LectureUnitProcessingStatusDTO(Long lectureUnitId, ProcessingPhase phase, int retryCount, ZonedDateTime startedAt, String errorMessage) {

    /**
     * Create a DTO from a processing state entity.
     *
     * @param state the processing state entity
     * @return the DTO
     */
    public static LectureUnitProcessingStatusDTO of(LectureUnitProcessingState state) {
        return new LectureUnitProcessingStatusDTO(state.getLectureUnit().getId(), state.getPhase(), state.getRetryCount(), state.getStartedAt(), state.getErrorMessage());
    }

    /**
     * Create a DTO for a lecture unit with no processing state (idle).
     *
     * @param lectureUnitId the ID of the lecture unit
     * @return the DTO
     */
    public static LectureUnitProcessingStatusDTO idle(Long lectureUnitId) {
        return new LectureUnitProcessingStatusDTO(lectureUnitId, ProcessingPhase.IDLE, 0, null, null);
    }
}
