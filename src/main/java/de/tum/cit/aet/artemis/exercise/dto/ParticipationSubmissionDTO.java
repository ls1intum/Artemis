package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Lean submission information nested in a participation response.
 *
 * @param id                     the unique identifier of the submission
 * @param submitted              whether the submission was submitted, if known
 * @param submissionDate         the submission time, if available
 * @param submissionExerciseType the exercise-type discriminator of the submission
 * @param commitHash             the programming commit hash, if this is a programming submission
 * @param results                initialized lean results, or absent when results were not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationSubmissionDTO(Long id, @Nullable Boolean submitted, @Nullable ZonedDateTime submissionDate, String submissionExerciseType, @Nullable String commitHash,
        @Nullable List<ParticipationSubmissionResultDTO> results) {

    /**
     * Creates a lean response DTO from a submission without initializing lazy results.
     *
     * @param submission the submission to map
     * @return the lean submission response
     */
    public static ParticipationSubmissionDTO of(Submission submission) {
        List<ParticipationSubmissionResultDTO> resultDTOs = null;
        if (Hibernate.isInitialized(submission.getResults())) {
            resultDTOs = submission.getResults().stream().filter(Objects::nonNull).map(ParticipationSubmissionResultDTO::of).toList();
        }
        String commitHash = submission instanceof ProgrammingSubmission programmingSubmission ? programmingSubmission.getCommitHash() : null;
        return new ParticipationSubmissionDTO(submission.getId(), submission.isSubmitted(), submission.getSubmissionDate(), submission.getSubmissionExerciseType(), commitHash,
                resultDTOs);
    }
}
