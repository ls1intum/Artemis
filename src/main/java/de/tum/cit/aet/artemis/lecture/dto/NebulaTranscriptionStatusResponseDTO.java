package de.tum.cit.aet.artemis.lecture.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.NebulaTranscriptionStatus;

/**
 * DTO for the response from Nebula's transcription status endpoint.
 * Contains the current status of a transcription job and the result data when completed.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NebulaTranscriptionStatusResponseDTO(@NotNull NebulaTranscriptionStatus status, String error, String language, List<LectureTranscriptionSegment> segments) {

    /**
     * Converts this status response to a LectureTranscriptionDTO when the transcription is completed.
     * Should only be called when status is DONE.
     *
     * @param lectureUnitId The ID of the lecture unit this transcription belongs to
     * @return LectureTranscriptionDTO containing the transcription data
     * @throws IllegalStateException if called when status is not DONE or if language/segments are missing
     */
    public LectureTranscriptionDTO toLectureTranscriptionDTO(Long lectureUnitId) {
        if (!status.isCompleted()) {
            throw new IllegalStateException("Cannot convert to LectureTranscriptionDTO when status is not DONE. Current status: " + status);
        }
        if (language == null || segments == null) {
            throw new IllegalStateException("Cannot convert to LectureTranscriptionDTO: language or segments are missing");
        }
        return new LectureTranscriptionDTO(lectureUnitId, language, segments);
    }

    /**
     * Checks if the transcription job is completed successfully.
     *
     * @return true if status is DONE
     */
    public boolean isCompleted() {
        return status.isCompleted();
    }

    /**
     * Checks if the transcription job has failed.
     *
     * @return true if status is ERROR
     */
    public boolean hasFailed() {
        return status.hasFailed();
    }

    /**
     * Checks if the transcription job is still in progress.
     *
     * @return true if status is PENDING, RUNNING, or PROCESSING
     */
    public boolean isInProgress() {
        return status.isInProgress();
    }
}
