package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * A programming submission with its results nested underneath, exactly where the entity wire puts them today.
 * <p>
 * Relocating the results out of the submission is the regression this shape exists to prevent: the repository view and
 * the programming-exercise service sort the submissions and read {@code last().results}. {@code submissionExerciseType}
 * is the constant discriminator {@code "programming"} that Jackson emits for the entity subtype today.
 *
 * @param id                     the submission id
 * @param submissionExerciseType the constant discriminator {@code "programming"}
 * @param type                   how the submission was created (manual, instructor, test, ...)
 * @param submitted              whether the submission was submitted
 * @param submissionDate         when the submission was created
 * @param commitHash             the git commit hash of the submission
 * @param buildFailed            whether the build for this submission failed
 * @param results                the results of this submission; {@code null} when they are not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingSubmissionWithResultsDTO(Long id, String submissionExerciseType, SubmissionType type, Boolean submitted, ZonedDateTime submissionDate, String commitHash,
        Boolean buildFailed, List<ResultDTO> results) implements Serializable {

    /**
     * The constant Jackson subtype id of {@link ProgrammingSubmission}.
     */
    public static final String SUBMISSION_EXERCISE_TYPE = "programming";

    /**
     * Converts a programming submission and maps every loaded result through {@link ResultDTO#ofNested}. Results and
     * their feedback are only mapped when already initialized, so this never triggers a lazy load.
     *
     * @param submission the submission to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingSubmissionWithResultsDTO of(ProgrammingSubmission submission) {
        if (submission == null) {
            return null;
        }
        List<ResultDTO> resultDTOs = null;
        // Hibernate.isInitialized(null) is true, so the null check has to stand next to it.
        if (submission.getResults() != null && Hibernate.isInitialized(submission.getResults())) {
            // the results collection can hold null elements; Submission's own accessors guard against them too
            resultDTOs = submission.getResults().stream().filter(Objects::nonNull).map(ResultDTO::ofNested).toList();
        }
        return of(submission, resultDTOs);
    }

    /**
     * Converts a programming submission with an explicitly built result list. Callers that filter results (exam
     * masking, sensitive-information filtering) use this overload so they never have to mutate the managed entity
     * graph to shape the JSON.
     *
     * @param submission the submission to convert (may be {@code null})
     * @param results    the already-mapped results to nest under the submission (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingSubmissionWithResultsDTO of(ProgrammingSubmission submission, List<ResultDTO> results) {
        if (submission == null) {
            return null;
        }
        return new ProgrammingSubmissionWithResultsDTO(submission.getId(), SUBMISSION_EXERCISE_TYPE, submission.getType(), submission.isSubmitted(), submission.getSubmissionDate(),
                submission.getCommitHash(), submission.isBuildFailed(), results);
    }
}
