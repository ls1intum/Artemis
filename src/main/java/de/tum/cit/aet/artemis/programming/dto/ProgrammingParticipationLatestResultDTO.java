package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentNoteDTO;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

/**
 * The {@link Result} shape served by {@code programming-exercise-participations/{id}/latest-result-with-feedbacks}.
 * <p>
 * That route is annotated {@code @AllowedTools(ToolTokenType.SCORPIO)} and consumed by the out-of-repo IntelliJ
 * plugin, which cannot be grepped for readers, so this record carries a superset of today's serialized {@link Result}
 * payload rather than the slimmer {@link ResultDTO} used on nested/websocket surfaces: it keeps {@code assessor} and
 * {@code assessmentNote} (both present on the entity wire today, even though {@code Result.filterSensitiveInformation}
 * — called unconditionally by this route, for every caller — nulls them before the response is built) and it embeds
 * a richer {@code submission.participation.exercise} chain than {@link ResultDTO}'s {@code ParticipationDTO} slice
 * (which only carries {@code id}/{@code accuracyOfScores} for the course) because the exercise and its course group
 * names are the kind of field a native client reconstructs a title/access-rights view from.
 *
 * @param id                  the result id
 * @param exerciseId          the id of the exercise the result belongs to; a non-null column, so it is on today's wire
 * @param completionDate      when the result was completed
 * @param successful          whether the result is successful
 * @param score               the achieved score
 * @param rated               whether the result counts towards the final grade
 * @param submission          the submission the result belongs to; {@code null} when the caller did not request it
 * @param feedbacks           the feedback items of this result
 * @param assessmentType      automatic, semi-automatic or manual assessment
 * @param hasComplaint        whether the result has a complaint
 * @param exampleResult       whether this is an example result
 * @param testCaseCount       the total number of test cases
 * @param passedTestCaseCount the number of passed test cases
 * @param codeIssueCount      the number of static code analysis issues
 * @param assessor            the assessor; stripped to {@code null} by {@code Result.filterSensitiveInformation} today
 * @param assessmentNote      the internal assessment note; same visibility rule as {@code assessor}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingParticipationLatestResultDTO(Long id, Long exerciseId, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated,
        SubmissionRefDTO submission, List<ResultDTO.FeedbackDTO> feedbacks, AssessmentType assessmentType, Boolean hasComplaint, Boolean exampleResult, Integer testCaseCount,
        Integer passedTestCaseCount, Integer codeIssueCount, UserNameDTO assessor, AssessmentNoteDTO assessmentNote) implements Serializable {

    /**
     * Converts a {@link Result} into a {@link ProgrammingParticipationLatestResultDTO}.
     *
     * @param result         the result to convert (may be {@code null})
     * @param withSubmission whether the nested submission should be populated
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingParticipationLatestResultDTO of(Result result, boolean withSubmission) {
        if (result == null) {
            return null;
        }
        SubmissionRefDTO submissionDTO = withSubmission ? SubmissionRefDTO.of(result.getSubmission()) : null;
        // Hibernate.isInitialized(null) is true, so every guard needs its own null check.
        var feedbacks = result.getFeedbacks();
        List<ResultDTO.FeedbackDTO> feedbackDTOs = feedbacks != null && Hibernate.isInitialized(feedbacks) ? feedbacks.stream().map(ResultDTO.FeedbackDTO::of).toList() : null;
        var assessorEntity = result.getAssessor();
        UserNameDTO assessor = assessorEntity != null && Hibernate.isInitialized(assessorEntity) ? UserNameDTO.of(assessorEntity) : null;
        AssessmentNoteDTO assessmentNote = AssessmentNoteDTO.of(result.getAssessmentNote());
        return new ProgrammingParticipationLatestResultDTO(result.getId(), result.getExerciseId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(),
                result.isRated(), submissionDTO, feedbackDTOs, result.getAssessmentType(), result.hasComplaint(), result.isExampleResult(), result.getTestCaseCount(),
                result.getPassedTestCaseCount(), result.getCodeIssueCount(), assessor, assessmentNote);
    }

    /**
     * The submission slice nested under {@link ProgrammingParticipationLatestResultDTO}, carrying its own
     * participation and (for the SCORPIO superset) the exercise the participation belongs to.
     * <p>
     * The components are the full set of properties {@link ProgrammingSubmission} serialized at this position before
     * this record existed, i.e. every non-{@code @JsonIgnore} property of the entity and its {@link Submission}
     * super class, including the {@code submissionExerciseType} type id and the computed {@code durationInMinutes}.
     * The one entity property that is deliberately absent is {@code results}: {@code Result.submission} carries
     * {@code @JsonIgnoreProperties({"results"})}, so the nested result list was never on this wire.
     *
     * @param id                     the submission id
     * @param submissionDate         when the submission was created
     * @param commitHash             the git commit hash of the submission
     * @param type                   how the submission was created (manual, instructor, test, ...)
     * @param submissionExerciseType the constant discriminator {@code "programming"}
     * @param submitted              whether the submission was submitted; never {@code null}, as on the entity wire
     * @param exampleSubmission      whether this is an example submission
     * @param buildFailed            whether the build of this submission failed
     * @param empty                  the constant {@code false} a programming submission reports for {@code isEmpty()}
     * @param durationInMinutes      minutes between participation start and submission; {@code null} when either
     *                                   date is missing
     * @param buildLogEntries        the build logs, but only when they are already loaded: this route does not fetch
     *                                   them, and forcing the lazy collection here would add queries to every call.
     *                                   The plugin reads build logs from {@code participations/{id}/buildlogs}
     * @param participation          the participation the submission belongs to
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SubmissionRefDTO(Long id, ZonedDateTime submissionDate, String commitHash, SubmissionType type, String submissionExerciseType, Boolean submitted,
            Boolean exampleSubmission, Boolean buildFailed, Boolean empty, Long durationInMinutes, List<BuildLogEntryDTO> buildLogEntries, ParticipationRefDTO participation)
            implements Serializable {

        private static final String SUBMISSION_EXERCISE_TYPE = "programming";

        /**
         * Converts a {@link Submission} into a {@link SubmissionRefDTO}. Only {@link ProgrammingSubmission}s are
         * supported; any other submission type maps to {@code null} (this route only ever serves programming
         * exercise participations).
         *
         * @param submission the submission to convert (may be {@code null})
         * @return the converted DTO, or {@code null} if the input was {@code null} or not a programming submission
         */
        public static SubmissionRefDTO of(Submission submission) {
            if (!(submission instanceof ProgrammingSubmission programmingSubmission)) {
                return null;
            }
            List<BuildLogEntry> entries = programmingSubmission.getBuildLogEntries();
            List<BuildLogEntryDTO> buildLogEntries = entries != null && Hibernate.isInitialized(entries) ? entries.stream().map(BuildLogEntryDTO::of).toList() : null;
            Participation participation = programmingSubmission.getParticipation();
            // getDurationInMinutes() reads the participation's initialization date, so it must not run on a proxy.
            Long durationInMinutes = participation != null && Hibernate.isInitialized(participation) ? programmingSubmission.getDurationInMinutes() : null;
            return new SubmissionRefDTO(programmingSubmission.getId(), programmingSubmission.getSubmissionDate(), programmingSubmission.getCommitHash(),
                    programmingSubmission.getType(), SUBMISSION_EXERCISE_TYPE, programmingSubmission.isSubmitted(), programmingSubmission.isExampleSubmission(),
                    programmingSubmission.isBuildFailed(), programmingSubmission.isEmpty(), durationInMinutes, buildLogEntries, ParticipationRefDTO.of(participation));
        }
    }

    /**
     * The participation slice nested under {@link SubmissionRefDTO}.
     *
     * @param id       the participation id
     * @param type     the constant discriminator (e.g. {@code "programming"})
     * @param exercise the nested exercise; {@code null} when not a programming exercise or not initialized
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationRefDTO(Long id, String type, ProgrammingExerciseResponseDTO exercise) implements Serializable {

        /**
         * Converts a {@link Participation} into a {@link ParticipationRefDTO}.
         *
         * @param participation the participation to convert (may be {@code null})
         * @return the converted DTO, or {@code null} if the input was {@code null}
         */
        public static ParticipationRefDTO of(Participation participation) {
            if (participation == null) {
                return null;
            }
            ProgrammingExerciseResponseDTO exerciseDTO = participation.getExercise() instanceof ProgrammingExercise programmingExercise
                    && Hibernate.isInitialized(programmingExercise) ? ProgrammingExerciseResponseDTO.of(programmingExercise) : null;
            return new ParticipationRefDTO(participation.getId(), participation.getType(), exerciseDTO);
        }
    }
}
