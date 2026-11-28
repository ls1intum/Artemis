package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;

/**
 * DTO containing Submission information.
 * This does not include large reference attributes in order to send minimal data to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionDTO(Long id, Boolean submitted, SubmissionType type, Boolean exampleSubmission, ZonedDateTime submissionDate, String commitHash, Boolean buildFailed,
        ParticipationDTO participation, String submissionExerciseType, boolean isProcessing, ZonedDateTime buildStartDate, ZonedDateTime estimatedCompletionDate)
        implements Serializable {
}
