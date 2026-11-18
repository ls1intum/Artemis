package de.tum.cit.aet.artemis.assessment.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.TeamDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * One row of the exercise scores export: a result, its total points and the points per grading criterion.
 *
 * @param result             the result projected to what the CSV export reads
 * @param totalPoints        the total points of the result
 * @param pointsPerCriterion Map of {@link GradingCriterion#getId()} to the result points in that category.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultWithPointsPerGradingCriterionDTO(ResultForExportDTO result, Double totalPoints, Map<Long, Double> pointsPerCriterion) {

    /**
     * The result as read by the exercise scores export: the score, the participant behind the submission and the
     * feedback (for the per-test-case columns of programming exercises).
     *
     * @param id             the result id
     * @param score          the score in percent
     * @param completionDate the completion date
     * @param submission     the submission the result belongs to, absent when the caller did not request submissions
     * @param feedbacks      the feedback of the result
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ResultForExportDTO(Long id, Double score, ZonedDateTime completionDate, SubmissionForExportDTO submission, List<FeedbackDTO> feedbacks) implements Serializable {

        /**
         * Converts a result into its export projection. Lazy associations are guarded with {@link Hibernate#isInitialized}
         * so an uninitialized proxy maps to null instead of failing outside the session.
         *
         * @param result to convert
         * @return the converted DTO
         */
        public static ResultForExportDTO of(Result result) {
            SubmissionForExportDTO submission = null;
            if (result.getSubmission() != null && Hibernate.isInitialized(result.getSubmission())) {
                submission = SubmissionForExportDTO.of(result.getSubmission());
            }
            List<FeedbackDTO> feedbacks = null;
            if (Hibernate.isInitialized(result.getFeedbacks())) {
                feedbacks = result.getFeedbacks().stream().map(FeedbackDTO::of).toList();
            }
            return new ResultForExportDTO(result.getId(), result.getScore(), result.getCompletionDate(), submission, feedbacks);
        }
    }

    /**
     * @param id            the submission id
     * @param participation the participation the submission belongs to
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SubmissionForExportDTO(Long id, ParticipationForExportDTO participation) implements Serializable {

        private static SubmissionForExportDTO of(Submission submission) {
            ParticipationForExportDTO participation = null;
            if (submission.getParticipation() != null && Hibernate.isInitialized(submission.getParticipation())) {
                participation = ParticipationForExportDTO.of(submission.getParticipation());
            }
            return new SubmissionForExportDTO(submission.getId(), participation);
        }
    }

    /**
     * The participant columns of the export: name and identifier of the student or team, the team members and, for
     * programming exercises, the repository link.
     *
     * @param id                    the participation id
     * @param participantName       the name of the student or team
     * @param participantIdentifier the login of the student or the short name of the team
     * @param repositoryUri         the repository URI of a programming participation, null otherwise
     * @param team                  the team of a team participation, null for individual participations
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationForExportDTO(Long id, String participantName, String participantIdentifier, String repositoryUri, TeamDTO team) implements Serializable {

        private static ParticipationForExportDTO of(Participation participation) {
            String participantName = null;
            String participantIdentifier = null;
            TeamDTO team = null;
            if (participation instanceof StudentParticipation studentParticipation) {
                participantName = studentParticipation.getParticipantName();
                participantIdentifier = studentParticipation.getParticipantIdentifier();
                team = studentParticipation.getTeam().map(TeamDTO::of).orElse(null);
            }
            String repositoryUri = participation instanceof ProgrammingExerciseStudentParticipation programmingParticipation ? programmingParticipation.getRepositoryUri() : null;
            return new ParticipationForExportDTO(participation.getId(), participantName, participantIdentifier, repositoryUri, team);
        }
    }
}
