package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * Minimal projection of a submission whose assessment is currently locked by a tutor, used by the exam assessment-locks
 * table ({@code assessment-locks.component}). It carries exactly what that table renders and what the cancel action needs:
 * the submission id/date and its polymorphic type discriminator, the participation id, the exercise id/type/title (for the
 * icon and the "open assessment" routing), and the latest result's score/completion date (the client derives
 * {@code latestResult} from {@code results} via {@code reconnectSubmissions}, so the results are carried as a list).
 * <p>
 * The wire shape deliberately mirrors the subset of the entity {@link Submission} that the table reads, because the same
 * table also renders the untouched course locked-submissions endpoint (which still returns entities).
 *
 * @param id                     the id of the submission
 * @param submissionDate         when the submission was submitted
 * @param submissionExerciseType the polymorphic submission-type discriminator ("programming", "text", ...) the cancel action switches on
 * @param participation          the participation (id + its exercise) the submission belongs to
 * @param results                the results of the submission; the client uses the last one as the latest (in-progress) result
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LockedExamSubmissionDTO(Long id, ZonedDateTime submissionDate, String submissionExerciseType, LockedSubmissionParticipationDTO participation,
        List<LockedSubmissionResultDTO> results) {

    /**
     * Slim participation of a locked submission: only the id and the exercise the assessment-locks table reads.
     *
     * @param id       the id of the participation (used for text-assessment routing)
     * @param exercise the exercise the participation belongs to
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LockedSubmissionParticipationDTO(Long id, LockedSubmissionExerciseDTO exercise) {
    }

    /**
     * Slim exercise of a locked submission: the id/type/title the assessment-locks table renders and routes on.
     *
     * @param id    the id of the exercise
     * @param type  the exercise-type discriminator ("programming", "text", ...) used for the icon and the assessment route
     * @param title the title of the exercise
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LockedSubmissionExerciseDTO(Long id, String type, String title) {
    }

    /**
     * Slim result of a locked submission: only the score and completion date the assessment-locks table reads.
     *
     * @param id             the id of the result
     * @param score          the achieved score
     * @param completionDate the completion date (null while the assessment is still locked/in progress)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LockedSubmissionResultDTO(Long id, Double score, ZonedDateTime completionDate) {
    }

    /**
     * Converts a locked {@link Submission} into its slim exam-locks projection.
     *
     * @param submission the submission whose assessment is locked (with participation, exercise and results loaded)
     * @return the projected DTO
     */
    public static LockedExamSubmissionDTO of(Submission submission) {
        Participation participation = submission.getParticipation();
        LockedSubmissionParticipationDTO participationDTO = null;
        if (participation != null) {
            LockedSubmissionExerciseDTO exerciseDTO = null;
            var exercise = participation.getExercise();
            if (exercise != null) {
                exerciseDTO = new LockedSubmissionExerciseDTO(exercise.getId(), exercise.getExerciseType() != null ? exercise.getExerciseType().getValue() : null,
                        exercise.getTitle());
            }
            participationDTO = new LockedSubmissionParticipationDTO(participation.getId(), exerciseDTO);
        }

        List<LockedSubmissionResultDTO> resultDTOs = submission.getResults() == null ? List.of()
                : submission.getResults().stream().filter(Objects::nonNull).map(LockedExamSubmissionDTO::toResultDTO).toList();

        return new LockedExamSubmissionDTO(submission.getId(), submission.getSubmissionDate(), submission.getSubmissionExerciseType(), participationDTO, resultDTOs);
    }

    private static LockedSubmissionResultDTO toResultDTO(Result result) {
        return new LockedSubmissionResultDTO(result.getId(), result.getScore(), result.getCompletionDate());
    }
}
