package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * A programming submission as the tutor assessment surfaces serialize it.
 * <p>
 * The results stay at the top level of the submission, where the assessment dashboard and the tutor editor read them:
 * the dashboard indexes {@code results[correctionRound]} positionally, so a slot that holds no result for the
 * requested round must survive as an explicit {@code null}. That is why this record uses the bare
 * {@code @JsonInclude()} — under {@code NON_EMPTY} an empty result list would vanish entirely instead of arriving as
 * {@code []}, and the positional {@code null} slots of the exam path would be dropped.
 *
 * @param id                     the submission id
 * @param submissionExerciseType the constant discriminator {@code "programming"}
 * @param type                   how the submission was created (manual, instructor, test, ...)
 * @param submitted              whether the submission was submitted
 * @param submissionDate         when the submission was created
 * @param commitHash             the git commit hash of the submission
 * @param buildFailed            whether the build for this submission failed
 * @param participation          the participation the submission belongs to, with its nested exercise
 * @param results                the results relevant for the requested correction round; entries may be {@code null}
 */
@JsonInclude()
public record ProgrammingSubmissionForAssessmentDTO(Long id, String submissionExerciseType, SubmissionType type, Boolean submitted, ZonedDateTime submissionDate, String commitHash,
        Boolean buildFailed, ProgrammingExerciseStudentParticipationDTO participation, List<ProgrammingAssessmentResultDTO> results) implements Serializable {

    /**
     * The constant Jackson subtype id of {@link ProgrammingSubmission}.
     */
    public static final String SUBMISSION_EXERCISE_TYPE = "programming";

    /**
     * Converts a submission together with every result that is already loaded on it. Used where the endpoint does not
     * pick a specific correction round.
     *
     * @param submission the submission to convert (may be {@code null})
     * @param exercise   the already-mapped nested exercise (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingSubmissionForAssessmentDTO ofWithLoadedResults(ProgrammingSubmission submission, ProgrammingExerciseResponseDTO exercise) {
        if (submission == null) {
            return null;
        }
        // Hibernate.isInitialized(null) is true, so the null check has to stand next to it.
        Collection<Result> results = submission.getResults() != null && Hibernate.isInitialized(submission.getResults()) ? submission.getResults() : null;
        return of(submission, exercise, results);
    }

    /**
     * Converts a submission together with the results that should be shown for the requested correction round.
     * <p>
     * The submission list of the nested participation is left {@code null} on purpose: the client re-attaches the
     * submission itself ({@code participation.submissions = [submission]}), so re-emitting it here would only repeat
     * the payload.
     *
     * @param submission the submission to convert (may be {@code null})
     * @param exercise   the already-mapped nested exercise (may be {@code null})
     * @param results    the results to nest under the submission; null elements are skipped
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingSubmissionForAssessmentDTO of(ProgrammingSubmission submission, ProgrammingExerciseResponseDTO exercise, Collection<Result> results) {
        if (submission == null) {
            return null;
        }
        ProgrammingExerciseStudentParticipationDTO participation = null;
        if (submission.getParticipation() instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            participation = ProgrammingExerciseStudentParticipationDTO.of(studentParticipation, exercise, null);
        }
        List<ProgrammingAssessmentResultDTO> resultDTOs = results == null ? null : results.stream().filter(Objects::nonNull).map(ProgrammingAssessmentResultDTO::of).toList();
        return new ProgrammingSubmissionForAssessmentDTO(submission.getId(), SUBMISSION_EXERCISE_TYPE, submission.getType(), submission.isSubmitted(),
                submission.getSubmissionDate(), submission.getCommitHash(), submission.isBuildFailed(), participation, resultDTOs);
    }
}
