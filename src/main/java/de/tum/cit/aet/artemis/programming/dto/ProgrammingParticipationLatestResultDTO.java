package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentNoteDTO;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

/**
 * The {@link Result} shape served by {@code programming-exercise-participations/{id}/latest-result-with-feedbacks}.
 * <p>
 * That route is annotated {@code @AllowedTools(ToolTokenType.SCORPIO)} and consumed by the out-of-repo IntelliJ
 * plugin, which cannot be grepped for readers, so every record in this file carries a superset of the serialized
 * {@link Result} payload the route emitted before the DTOs existed, rather than the slimmer {@link ResultDTO} used on
 * nested/websocket surfaces. The shapes here were derived from a dump of the entity payload, not from reading the
 * entities, because {@code NON_EMPTY} hides any property whose fixture value happens to be null or empty.
 * <p>
 * Two consequences of that dump are worth stating, because they are not obvious from the route signature:
 * <ul>
 * <li>{@code Result.submission} is an eager {@code @ManyToOne}, so the entity payload carried the whole submission
 * (and through it the participation, the participant and the exercise) for <em>both</em> values of
 * {@code withSubmission}. The flag only ever selected the fetch graph, never the shape. This record therefore always
 * populates {@code submission}.</li>
 * <li>{@code assessor} and {@code assessmentNote} stay on the record even though
 * {@code Result.filterSensitiveInformation} — called unconditionally by this route, for every caller — nulls them
 * before the response is built.</li>
 * </ul>
 *
 * @param id                  the result id
 * @param exerciseId          the id of the exercise the result belongs to; a non-null column, so it is on today's wire
 * @param completionDate      when the result was completed
 * @param successful          whether the result is successful
 * @param score               the achieved score
 * @param rated               whether the result counts towards the final grade
 * @param submission          the submission the result belongs to
 * @param feedbacks           the feedback items of this result
 * @param assessmentType      automatic, semi-automatic or manual assessment
 * @param correctionRound     which correction round this result belongs to; {@code null} for automatic results
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
        SubmissionRefDTO submission, List<FeedbackRefDTO> feedbacks, AssessmentType assessmentType, Integer correctionRound, Boolean hasComplaint, Boolean exampleResult,
        Integer testCaseCount, Integer passedTestCaseCount, Integer codeIssueCount, UserNameDTO assessor, AssessmentNoteDTO assessmentNote) implements Serializable {

    /**
     * Converts a {@link Result} into a {@link ProgrammingParticipationLatestResultDTO}.
     *
     * @param result the result to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingParticipationLatestResultDTO of(Result result) {
        if (result == null) {
            return null;
        }
        // Hibernate.isInitialized(null) is true, so every guard needs its own null check.
        var submissionEntity = result.getSubmission();
        SubmissionRefDTO submissionDTO = submissionEntity != null && Hibernate.isInitialized(submissionEntity) ? SubmissionRefDTO.of(submissionEntity) : null;
        var feedbacks = result.getFeedbacks();
        List<FeedbackRefDTO> feedbackDTOs = feedbacks != null && Hibernate.isInitialized(feedbacks) ? feedbacks.stream().map(FeedbackRefDTO::of).toList() : null;
        var assessorEntity = result.getAssessor();
        UserNameDTO assessor = assessorEntity != null && Hibernate.isInitialized(assessorEntity) ? UserNameDTO.of(assessorEntity) : null;
        AssessmentNoteDTO assessmentNote = AssessmentNoteDTO.of(result.getAssessmentNote());
        return new ProgrammingParticipationLatestResultDTO(result.getId(), result.getExerciseId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(),
                result.isRated(), submissionDTO, feedbackDTOs, result.getAssessmentType(), result.getCorrectionRound(), result.hasComplaint(), result.isExampleResult(),
                result.getTestCaseCount(), result.getPassedTestCaseCount(), result.getCodeIssueCount(), assessor, assessmentNote);
    }

    /**
     * The feedback slice nested under {@link ProgrammingParticipationLatestResultDTO}.
     * <p>
     * This is deliberately not {@link ResultDTO.FeedbackDTO}: that record is shared with the websocket surfaces and
     * omits {@code gradingInstruction}, which the {@link Feedback} entity did put on this route's wire. The one
     * entity property that is not a component is {@code result}: {@code Result.feedbacks} carries
     * {@code @JsonIgnoreProperties("result")}, so the back reference was never serialized here. {@code longFeedbackText}
     * is {@code @JsonIgnore} on the entity — the plugin reads it from {@code feedbacks/{id}/long-feedback}.
     *
     * @param id                  the feedback id
     * @param text                the feedback text, i.e. the test case name for automatic feedback
     * @param detailText          the detail text; a preview when a long feedback text exists
     * @param hasLongFeedbackText whether a {@code LongFeedbackText} has to be fetched separately
     * @param reference           the reference to the assessed element
     * @param credits             the points this feedback is worth
     * @param positive            whether the feedback is positive
     * @param type                automatic, manual or manual-unreferenced feedback
     * @param visibility          when students may see this feedback
     * @param testCase            the test case that produced this feedback; {@code null} for manual feedback
     * @param gradingInstruction  the structured grading instruction the assessor applied; {@code null} without one
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FeedbackRefDTO(Long id, String text, String detailText, boolean hasLongFeedbackText, String reference, Double credits, Boolean positive, FeedbackType type,
            Visibility visibility, TestCaseRefDTO testCase, GradingInstructionRefDTO gradingInstruction) implements Serializable {

        /**
         * Converts a {@link Feedback} into a {@link FeedbackRefDTO}.
         *
         * @param feedback the feedback to convert
         * @return the converted DTO
         */
        public static FeedbackRefDTO of(Feedback feedback) {
            var testCase = feedback.getTestCase();
            var gradingInstruction = feedback.getGradingInstruction();
            return new FeedbackRefDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(),
                    feedback.getCredits(), feedback.isPositive(), feedback.getType(), feedback.getVisibility(),
                    testCase != null && Hibernate.isInitialized(testCase) ? TestCaseRefDTO.of(testCase) : null,
                    gradingInstruction != null && Hibernate.isInitialized(gradingInstruction) ? GradingInstructionRefDTO.of(gradingInstruction) : null);
        }
    }

    /**
     * The test case slice nested under {@link FeedbackRefDTO}.
     * <p>
     * This is deliberately not {@link ResultDTO.TestCaseDTO}, which carries only {@code id} and {@code testName}:
     * {@code Feedback.testCase} is annotated {@code @JsonIgnoreProperties({"tasks", "exercise"})}, so every other
     * property of {@link ProgrammingExerciseTestCase} was on this route's wire. {@code testName} is nulled by
     * {@code Result.createFilteredFeedbacks} when the exercise hides test names from students, which is a value
     * change rather than a key change and therefore reproduced by mapping whatever the filtered entity holds.
     *
     * @param id              the test case id
     * @param testName        the name of the test case; {@code null} when hidden from students
     * @param weight          the weight of the test case in the score computation
     * @param active          whether the test case counts towards the score
     * @param visibility      when students may see results of this test case
     * @param bonusMultiplier the multiplier applied to the achieved points
     * @param bonusPoints     the absolute bonus points of the test case
     * @param type            behavioural, structural or default test case
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TestCaseRefDTO(Long id, String testName, Double weight, Boolean active, Visibility visibility, Double bonusMultiplier, Double bonusPoints,
            ProgrammingExerciseTestCaseType type) implements Serializable {

        /**
         * Converts a {@link ProgrammingExerciseTestCase} into a {@link TestCaseRefDTO}.
         *
         * @param testCase the test case to convert (may be {@code null})
         * @return the converted DTO, or {@code null} if the input was {@code null}
         */
        public static TestCaseRefDTO of(ProgrammingExerciseTestCase testCase) {
            if (testCase == null) {
                return null;
            }
            return new TestCaseRefDTO(testCase.getId(), testCase.getTestName(), testCase.getWeight(), testCase.isActive(), testCase.getVisibility(), testCase.getBonusMultiplier(),
                    testCase.getBonusPoints(), testCase.getType());
        }
    }

    /**
     * The structured grading instruction nested under {@link FeedbackRefDTO}.
     * <p>
     * The components are every property {@link GradingInstruction} serialized at this position. Its two associations
     * are deliberately absent, because the entity payload never carried them either: {@code gradingCriterion} is a
     * lazy {@code @ManyToOne} that this route's fetch graph never initializes, and {@code feedbacks} is the lazy back
     * reference to the very feedback list this instruction hangs under.
     *
     * @param id                     the grading instruction id
     * @param credits                the points a tutor grants by applying this instruction
     * @param gradingScale           the level of performance, e.g. {@code "good"}
     * @param instructionDescription the description shown to the assessor
     * @param feedback               the feedback text proposed for this instruction
     * @param usageCount             how often the instruction may be applied per submission
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record GradingInstructionRefDTO(Long id, double credits, String gradingScale, String instructionDescription, String feedback, int usageCount) implements Serializable {

        /**
         * Converts a {@link GradingInstruction} into a {@link GradingInstructionRefDTO}.
         *
         * @param gradingInstruction the grading instruction to convert (may be {@code null})
         * @return the converted DTO, or {@code null} if the input was {@code null}
         */
        public static GradingInstructionRefDTO of(GradingInstruction gradingInstruction) {
            if (gradingInstruction == null) {
                return null;
            }
            return new GradingInstructionRefDTO(gradingInstruction.getId(), gradingInstruction.getCredits(), gradingInstruction.getGradingScale(),
                    gradingInstruction.getInstructionDescription(), gradingInstruction.getFeedback(), gradingInstruction.getUsageCount());
        }
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
            Set<BuildLogEntry> entries = programmingSubmission.getBuildLogEntries();
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
     * <p>
     * The components cover every property the concrete {@link Participation} subclasses put on this route's wire.
     * Which of them are populated depends on the subclass, exactly as it did on the entity payload: the participant
     * slots come from {@link StudentParticipation}, the repository slots from
     * {@link ProgrammingExerciseParticipation}, and {@code branch} only exists on
     * {@link ProgrammingExerciseStudentParticipation}. The exercise sits in two different slots for the same reason:
     * a student participation serialized it as {@code exercise}, while a template or solution participation
     * {@code @JsonIgnore}s that getter and serialized {@code programmingExercise} instead.
     * <p>
     * Two entity properties are deliberately absent, because the entity payload did not carry them either:
     * {@code submissions} is a lazy collection that this route never initializes (and re-emitting it would repeat the
     * submission subtree), and {@code submissionCount} is a transient that only the participation dashboards set.
     *
     * @param id                           the participation id
     * @param type                         the constant discriminator, e.g. {@code "programming"}
     * @param initializationState          how far the participation got in its lifecycle
     * @param initializationDate           when the participation was started
     * @param individualDueDate            the individual due date, when an instructor set one
     * @param testRun                      whether this is an exam test run or a practice-mode participation
     * @param attempt                      the attempt number, used by test exams
     * @param presentationScore            the presentation score of a student participation
     * @param student                      the participating student, for an individual participation
     * @param team                         the participating team, for a team participation
     * @param participantIdentifier        the login of the student or the short name of the team
     * @param participantName              the display name of the student or the team
     * @param repositoryUri                the URI of the participation repository
     * @param buildPlanId                  the id of the build plan
     * @param branch                       the default branch stored for this participation
     * @param userIndependentRepositoryUri the repository URI with the user info stripped from the authority
     * @param exercise                     the exercise of a student participation
     * @param programmingExercise          the exercise of a template or solution participation
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationRefDTO(Long id, String type, InitializationState initializationState, ZonedDateTime initializationDate, ZonedDateTime individualDueDate,
            boolean testRun, int attempt, Double presentationScore, ParticipantRefDTO student, TeamRefDTO team, String participantIdentifier, String participantName,
            String repositoryUri, String buildPlanId, String branch, String userIndependentRepositoryUri, ProgrammingExerciseResponseDTO exercise,
            ProgrammingExerciseResponseDTO programmingExercise) implements Serializable {

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
            Double presentationScore = null;
            ParticipantRefDTO student = null;
            TeamRefDTO team = null;
            String participantIdentifier = null;
            String participantName = null;
            if (participation instanceof StudentParticipation studentParticipation) {
                presentationScore = studentParticipation.getPresentationScore();
                student = studentParticipation.getStudent().map(ParticipantRefDTO::of).orElse(null);
                team = studentParticipation.getTeam().map(TeamRefDTO::of).orElse(null);
                participantIdentifier = studentParticipation.getParticipantIdentifier();
                participantName = studentParticipation.getParticipantName();
            }
            String repositoryUri = null;
            String buildPlanId = null;
            String userIndependentRepositoryUri = null;
            if (participation instanceof ProgrammingExerciseParticipation programmingParticipation) {
                repositoryUri = programmingParticipation.getRepositoryUri();
                buildPlanId = programmingParticipation.getBuildPlanId();
                userIndependentRepositoryUri = programmingParticipation.getUserIndependentRepositoryUri();
            }
            String branch = participation instanceof ProgrammingExerciseStudentParticipation studentParticipation ? studentParticipation.getBranch() : null;
            // A template or solution participation @JsonIgnores getExercise() and serializes programmingExercise instead.
            boolean serializesProgrammingExercise = !(participation instanceof StudentParticipation);
            ProgrammingExerciseResponseDTO exerciseDTO = exerciseOf(participation);
            return new ParticipationRefDTO(participation.getId(), participation.getType(), participation.getInitializationState(), participation.getInitializationDate(),
                    participation.getIndividualDueDate(), participation.isTestRun(), participation.getAttempt(), presentationScore, student, team, participantIdentifier,
                    participantName, repositoryUri, buildPlanId, branch, userIndependentRepositoryUri, serializesProgrammingExercise ? null : exerciseDTO,
                    serializesProgrammingExercise ? exerciseDTO : null);
        }

        /**
         * Projects the exercise a participation belongs to, guarded against a proxy and against a non-programming
         * exercise.
         *
         * @param participation the participation being mapped
         * @return the projected exercise, or {@code null} when it is not loaded or not a programming exercise
         */
        private static ProgrammingExerciseResponseDTO exerciseOf(Participation participation) {
            if (participation.getExercise() instanceof ProgrammingExercise programmingExercise && Hibernate.isInitialized(programmingExercise)) {
                return ProgrammingExerciseResponseDTO.of(programmingExercise);
            }
            return null;
        }
    }

    /**
     * The participating user nested under {@link ParticipationRefDTO}.
     * <p>
     * The components are the scalar properties {@link User} serialized at this position. Its association slots
     * ({@code authorities}, {@code organizations}, {@code savedPosts}, {@code tutorialGroupRegistrations} and
     * {@code learnerProfile}) are absent because they are all lazy and this route's fetch graph never initializes
     * them, so the entity payload dropped them under {@code NON_EMPTY} as well. The special-case scalars
     * ({@code ltiCreated}, {@code memirisEnabled}, {@code resetDate}, {@code selectedLLMUsage} and its timestamp)
     * moved off the entity into their own tables (#13546) and left the wire with them.
     *
     * @param id                        the user id
     * @param login                     the login
     * @param name                      the display name, i.e. first and last name
     * @param firstName                 the first name
     * @param lastName                  the last name
     * @param email                     the email address
     * @param imageUrl                  the profile picture URL
     * @param langKey                   the preferred language
     * @param activated                 whether the account is activated
     * @param deleted                   whether the account is soft-deleted
     * @param internal                  whether the account is managed by Artemis rather than by an external system
     * @param testUser                  whether this is a test user
     * @param bot                       whether this is the Iris bot account
     * @param participantIdentifier     the login, under the name the participant interface uses
     * @param visibleRegistrationNumber the registration number, when a caller was allowed to unmask it
     * @param createdDate               when the account was created
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipantRefDTO(Long id, String login, String name, String firstName, String lastName, String email, String imageUrl, String langKey, boolean activated,
            boolean deleted, boolean internal, boolean testUser, boolean bot, String participantIdentifier, String visibleRegistrationNumber, Instant createdDate)
            implements Serializable {

        /**
         * Converts a {@link User} into a {@link ParticipantRefDTO}.
         *
         * @param user the user to convert (may be {@code null})
         * @return the converted DTO, or {@code null} if the input was {@code null}
         */
        public static ParticipantRefDTO of(User user) {
            if (user == null) {
                return null;
            }
            return new ParticipantRefDTO(user.getId(), user.getLogin(), user.getName(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getImageUrl(),
                    user.getLangKey(), user.getActivated(), user.isDeleted(), user.isInternal(), user.isTestUser(), user.isBot(), user.getParticipantIdentifier(),
                    user.getVisibleRegistrationNumber(), user.getCreatedDate());
        }
    }

    /**
     * The participating team nested under {@link ParticipationRefDTO}.
     * <p>
     * The components are every property {@link Team} serialized at this position; {@code exercise} is
     * {@code @JsonIgnore} on the entity, and {@code students} is a lazy {@code @ManyToMany} that is only mapped once
     * it is loaded, so a team participation never triggers an extra query here. {@link Team} re-enables the audit
     * columns its super class hides, so they are components too.
     *
     * @param id                    the team id
     * @param name                  the team name
     * @param shortName             the team short name, used as the participant identifier
     * @param image                 the team image URL
     * @param participantIdentifier the short name, under the name the participant interface uses
     * @param students              the team members; {@code null} when the collection is not loaded
     * @param owner                 the tutor owning the team
     * @param createdBy             the login of the creator
     * @param createdDate           when the team was created
     * @param lastModifiedBy        the login of the last editor
     * @param lastModifiedDate      when the team was last edited
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TeamRefDTO(Long id, String name, String shortName, String image, String participantIdentifier, List<ParticipantRefDTO> students, ParticipantRefDTO owner,
            String createdBy, Instant createdDate, String lastModifiedBy, Instant lastModifiedDate) implements Serializable {

        /**
         * Converts a {@link Team} into a {@link TeamRefDTO}.
         *
         * @param team the team to convert (may be {@code null})
         * @return the converted DTO, or {@code null} if the input was {@code null}
         */
        public static TeamRefDTO of(Team team) {
            if (team == null) {
                return null;
            }
            Set<User> studentEntities = team.getStudents();
            List<ParticipantRefDTO> students = studentEntities != null && Hibernate.isInitialized(studentEntities) ? studentEntities.stream().map(ParticipantRefDTO::of).toList()
                    : null;
            var ownerEntity = team.getOwner();
            ParticipantRefDTO owner = ownerEntity != null && Hibernate.isInitialized(ownerEntity) ? ParticipantRefDTO.of(ownerEntity) : null;
            return new TeamRefDTO(team.getId(), team.getName(), team.getShortName(), team.getImage(), team.getParticipantIdentifier(), students, owner, team.getCreatedBy(),
                    team.getCreatedDate(), team.getLastModifiedBy(), team.getLastModifiedDate());
        }
    }
}
