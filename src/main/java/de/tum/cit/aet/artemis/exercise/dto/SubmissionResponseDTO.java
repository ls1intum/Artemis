package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * A submission as the submission list endpoints report it.
 * <p>
 * The exercise-type specific components are filled for the matching subclass only, which is why {@code submissionExerciseType}
 * stays on the wire: it is the Jackson discriminator of {@link Submission} and the client switches on it.
 * <p>
 * {@code NON_EMPTY} drops empty collections, so {@code results} and {@code participation.submissions} are absent
 * rather than empty; every client reader of both already treats absent and empty the same way. Boolean wrappers
 * keep their {@code false} (pinned by the complaint dashboard wire test), which is what
 * {@code Result.successful === false} relies on.
 *
 * @param id                     the submission id
 * @param submitted              whether the student submitted
 * @param type                   how the submission was triggered
 * @param exampleSubmission      whether this is an example submission
 * @param submissionDate         when the submission was made
 * @param submissionExerciseType the polymorphic discriminator of {@link Submission}
 * @param participation          the participation the submission belongs to, without its exercise
 * @param results                the results of the submission, or null when they were not loaded
 * @param buildFailed            whether the programming build failed, for programming submissions
 * @param text                   the submitted text, for text submissions
 * @param language               the language of the text, for text submissions
 * @param model                  the submitted model, for modeling submissions
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionResponseDTO(Long id, Boolean submitted, @Nullable SubmissionType type, Boolean exampleSubmission, @Nullable ZonedDateTime submissionDate,
        String submissionExerciseType, @Nullable SubmissionParticipationDTO participation, @Nullable List<SubmissionResultDTO> results, @Nullable Boolean buildFailed,
        @Nullable String text, @Nullable Language language, @Nullable String model) {

    /**
     * Maps a submission without the sibling submissions of its participation.
     *
     * @param submission the submission to map
     * @return the submission as the response reports it
     */
    public static SubmissionResponseDTO of(Submission submission) {
        return of(submission, false);
    }

    /**
     * Maps a submission together with the sibling submissions of its participation.
     * <p>
     * The example-submission import table resolves the result it shows over all submissions of the participation, not
     * over the listed submission alone, so that endpoint needs the siblings.
     *
     * @param submission the submission to map
     * @return the submission as the response reports it
     */
    public static SubmissionResponseDTO ofWithParticipationSubmissions(Submission submission) {
        return of(submission, true);
    }

    private static SubmissionResponseDTO of(Submission submission, boolean includeParticipationSubmissions) {
        Objects.requireNonNull(submission, "The submission must be set");

        SubmissionParticipationDTO participation = null;
        if (submission.getParticipation() != null && Hibernate.isInitialized(submission.getParticipation())) {
            participation = SubmissionParticipationDTO.of(submission.getParticipation(), includeParticipationSubmissions);
        }

        Boolean buildFailed = null;
        String text = null;
        Language language = null;
        String model = null;
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            buildFailed = programmingSubmission.isBuildFailed();
        }
        else if (submission instanceof TextSubmission textSubmission) {
            text = textSubmission.getText();
            language = textSubmission.getLanguage();
        }
        else if (submission instanceof ModelingSubmission modelingSubmission) {
            model = modelingSubmission.getModel();
        }

        return new SubmissionResponseDTO(submission.getId(), submission.isSubmitted(), submission.getType(), submission.isExampleSubmission(), submission.getSubmissionDate(),
                submission.getSubmissionExerciseType(), participation, resultsOf(submission), buildFailed, text, language, model);
    }

    @Nullable
    private static List<SubmissionResultDTO> resultsOf(Submission submission) {
        if (!Hibernate.isInitialized(submission.getResults())) {
            return null;
        }
        return submission.getResults().stream().filter(Objects::nonNull).map(SubmissionResultDTO::of).toList();
    }

    /**
     * The participation of a listed submission, without its exercise.
     * <p>
     * The exercise is deliberately left out: all three list endpoints either strip it server-side or hold it as an
     * uninitialized proxy, so it never reached the client, and every consumer page passes the exercise in separately.
     *
     * @param id                  the participation id
     * @param type                the polymorphic discriminator of {@link Participation}
     * @param testRun             whether the participation is a practice or test run
     * @param initializationState the state the participation is in
     * @param initializationDate  when the participation started
     * @param individualDueDate   the individual due date, if one was set
     * @param participantName     the display name of the student or team, null where the participant is withheld
     * @param submissions         the other submissions of the participation, only where the caller asked for them
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SubmissionParticipationDTO(Long id, String type, boolean testRun, @Nullable InitializationState initializationState, @Nullable ZonedDateTime initializationDate,
            @Nullable ZonedDateTime individualDueDate, @Nullable String participantName, @Nullable List<ParticipationSubmissionDTO> submissions) {

        static SubmissionParticipationDTO of(Participation participation, boolean includeSubmissions) {
            String participantName = participation instanceof StudentParticipation studentParticipation ? studentParticipation.getParticipantName() : null;
            List<ParticipationSubmissionDTO> submissions = null;
            if (includeSubmissions && Hibernate.isInitialized(participation.getSubmissions())) {
                submissions = participation.getSubmissions().stream().filter(Objects::nonNull).map(ParticipationSubmissionDTO::of).toList();
            }
            return new SubmissionParticipationDTO(participation.getId(), participation.getType(), participation.isTestRun(), participation.getInitializationState(),
                    participation.getInitializationDate(), participation.getIndividualDueDate(), participantName, submissions);
        }
    }

    /**
     * A sibling submission of the participation, carrying only what the client needs to resolve the displayed result.
     *
     * @param id      the submission id
     * @param results the results of that submission, or null when they were not loaded
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationSubmissionDTO(Long id, @Nullable List<SubmissionResultDTO> results) {

        static ParticipationSubmissionDTO of(Submission submission) {
            return new ParticipationSubmissionDTO(submission.getId(), resultsOf(submission));
        }
    }

    /**
     * A result of a listed submission.
     * <p>
     * {@code testCaseCount}, {@code passedTestCaseCount} and {@code codeIssueCount} are real columns the client turns
     * into the "x of y passed tests" result string for programming exercises, and {@code correctionRound} is how the
     * assessment dashboard picks the result of the round it displays.
     *
     * @param id                  the result id
     * @param completionDate      when the assessment was completed
     * @param successful          whether the result is successful
     * @param score               the score in percent
     * @param rated               whether the result counts towards the exercise score
     * @param assessmentType      how the result was produced
     * @param correctionRound     the correction round the result belongs to
     * @param hasComplaint        whether a complaint exists for the result
     * @param exampleResult       whether the result belongs to an example submission
     * @param testCaseCount       the number of programming test cases
     * @param passedTestCaseCount the number of passed programming test cases
     * @param codeIssueCount      the number of static code analysis issues
     * @param assessor            the assessor, where it was loaded
     * @param feedbacks           the feedback of the result, where it was loaded
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SubmissionResultDTO(Long id, @Nullable ZonedDateTime completionDate, @Nullable Boolean successful, @Nullable Double score, @Nullable Boolean rated,
            @Nullable AssessmentType assessmentType, @Nullable Integer correctionRound, @Nullable Boolean hasComplaint, @Nullable Boolean exampleResult,
            @Nullable Integer testCaseCount, @Nullable Integer passedTestCaseCount, @Nullable Integer codeIssueCount, @Nullable UserNameDTO assessor,
            @Nullable List<FeedbackDTO> feedbacks) {

        static SubmissionResultDTO of(Result result) {
            UserNameDTO assessor = result.getAssessor() != null && Hibernate.isInitialized(result.getAssessor()) ? UserNameDTO.of(result.getAssessor()) : null;
            List<FeedbackDTO> feedbacks = Hibernate.isInitialized(result.getFeedbacks()) ? result.getFeedbacks().stream().filter(Objects::nonNull).map(FeedbackDTO::of).toList()
                    : null;
            return new SubmissionResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), result.getAssessmentType(),
                    result.getCorrectionRound(), result.hasComplaint(), result.isExampleResult(), result.getTestCaseCount(), result.getPassedTestCaseCount(),
                    result.getCodeIssueCount(), assessor, feedbacks);
        }
    }
}
