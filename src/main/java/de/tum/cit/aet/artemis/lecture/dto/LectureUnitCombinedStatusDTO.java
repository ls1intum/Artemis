package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;

/**
 * DTO representing the combined processing and transcription status of a lecture unit.
 * Used to efficiently load all status information for lecture units in a single request.
 * <p>
 * The stage fields expose the stage ledger the heartbeats maintain, so the client can render live
 * progress ("Indexing · 41/142"). They are the first slice of the observability payload; richer
 * ledger fields (stage timings, quality, verification) extend this DTO the same way.
 *
 * @param lectureUnitId       the ID of the lecture unit
 * @param processingPhase     current phase of the processing pipeline
 * @param retryCount          how many attempts the current work has consumed
 * @param startedAt           when the current phase started
 * @param processingErrorKey  i18n key of the failure reason; {@code null} unless FAILED
 * @param transcriptionStatus status of the stored transcription; may be {@code null}
 * @param stageName           name of the pipeline stage Iris last reported; {@code null} when unknown
 * @param stageProgress       progress counter within the stage; {@code null} when the stage has no counter
 * @param stageTotal          total work items of the stage; {@code null} when the stage has no counter
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitCombinedStatusDTO(Long lectureUnitId, ProcessingPhase processingPhase, int retryCount, ZonedDateTime startedAt, String processingErrorKey,
        TranscriptionStatus transcriptionStatus, String stageName, Integer stageProgress, Integer stageTotal) {

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
                    processingState.getErrorKey(), transcriptionStatus, processingState.getCurrentStage(), processingState.getStageProgress(), processingState.getStageTotal());
        }
        return new LectureUnitCombinedStatusDTO(unitId, ProcessingPhase.IDLE, 0, null, null, transcriptionStatus, null, null, null);
    }
}
