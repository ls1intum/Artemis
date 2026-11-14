package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;

/** Scalar queue data selected without loading submitted answers. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentQueueSubmissionDTO(Long id, Boolean submitted, SubmissionType type, Boolean exampleSubmission, ZonedDateTime submissionDate, String submissionExerciseType,
        Long participationId, InitializationState initializationState, ZonedDateTime initializationDate, ZonedDateTime individualDueDate, String participantName,
        Boolean buildFailed, String commitHash, Language language) {

    /** Converts Hibernate's entity discriminator into the existing wire discriminator. */
    public AssessmentQueueSubmissionDTO(Long id, Boolean submitted, SubmissionType type, Boolean exampleSubmission, ZonedDateTime submissionDate,
            Class<? extends Submission> submissionClass, Long participationId, InitializationState initializationState, ZonedDateTime initializationDate,
            ZonedDateTime individualDueDate, String participantName, Boolean buildFailed, String commitHash, Language language) {
        this(id, submitted, type, exampleSubmission, submissionDate,
                submissionClass == de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission.class ? "file-upload"
                        : submissionClass.getSimpleName().replace("Submission", "").toLowerCase(java.util.Locale.ROOT),
                participationId, initializationState, initializationDate, individualDueDate, participantName, buildFailed, commitHash, language);
    }

    /** Builds the queue response using scalar metadata and separately loaded results. */
    public SubmissionResponseDTO toResponse(List<AssessmentQueueResultDTO> results) {
        var participation = new SubmissionResponseDTO.SubmissionParticipationDTO(participationId, "programming".equals(submissionExerciseType) ? "programming" : "student", false,
                initializationState, initializationDate, individualDueDate, participantName, null);
        return new SubmissionResponseDTO(id, submitted, type, exampleSubmission, submissionDate, submissionExerciseType, participation,
                results.stream().map(AssessmentQueueResultDTO::toResponse).toList(), buildFailed, commitHash, null, language, null);
    }
}
