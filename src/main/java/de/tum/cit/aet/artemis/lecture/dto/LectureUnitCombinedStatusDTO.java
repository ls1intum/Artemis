package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;

/**
 * DTO representing the combined processing and transcription status of a lecture unit.
 * Used to efficiently load all status information for lecture units in a single request.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitCombinedStatusDTO(Long lectureUnitId, ProcessingPhase processingPhase, int retryCount, ZonedDateTime startedAt, String processingErrorKey,
        TranscriptionStatus transcriptionStatus) {

    /**
     * Create a DTO from a processing state entity and transcription status.
     *
     * @param unitId              the ID of the lecture unit
     * @param processingState     the processing state entity (may be null)
     * @param transcriptionStatus the transcription status (may be null)
     * @return the DTO
     */
    public static LectureUnitCombinedStatusDTO of(Long unitId, LectureUnitProcessingState processingState, TranscriptionStatus transcriptionStatus) {
        if (processingState != null) {
            return new LectureUnitCombinedStatusDTO(unitId, processingState.getPhase(), processingState.getRetryCount(), processingState.getStartedAt(),
                    processingState.getErrorKey(), transcriptionStatus);
        }
        return new LectureUnitCombinedStatusDTO(unitId, ProcessingPhase.IDLE, 0, null, null, transcriptionStatus);
    }
}
