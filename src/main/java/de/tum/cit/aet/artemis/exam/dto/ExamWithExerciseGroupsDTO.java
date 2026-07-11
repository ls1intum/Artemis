package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Response DTO for an {@link Exam} together with its exercise groups, returned by the detailed {@code GET} by id
 * ({@code withExerciseGroups=true}) and by {@code reset}.
 * <p>
 * It repeats the scalar exam core (kept flat on the wire so the client reads {@code exam.title} etc. unchanged) and adds
 * the two things those two screens read beyond the core: {@code numberOfExamUsers} (a transient set by
 * {@code ExamService.setExamProperties}) and the exercise groups with their exercises. {@code channelName} is
 * intentionally absent — neither the detailed get nor reset populates it today, and no consumer of these two responses
 * reads it (the edit round-trip uses the plain {@link ExamDTO} path).
 * <p>
 * Two factories serve the two fetch shapes. {@link #ofDetailed(Exam)} maps the fully hydrated graph (quiz-question count
 * and programming build-plan ids attached by {@code populate*}); {@link #ofReset(Exam)} maps the lighter reset graph
 * ({@code withDetails=false}), which hydrates neither quiz questions nor programming participations, so those are left
 * out — reset renders the exam-detail screen, which reads only {@code exercise.type} and
 * {@code exercise.numberOfParticipations}, both present via {@code setExamProperties}. Lazy sub-relations are guarded
 * with {@link Hibernate#isInitialized} so a factory never forces a load outside the transaction.
 * <p>
 * NOTE (post-merge unification): the nested {@link ExerciseGroupWithExercisesDTO} / {@link ExamExerciseDTO} records are
 * this endpoint's own, defined against {@code develop}. They deliberately parallel the shapes introduced by the
 * exercise-group-dtos work (#13097) — {@code ExerciseGroupDTO} / {@code ExerciseForExerciseGroupDTO} — extended with the
 * three fields the exam management table needs that the #13097 summary lacks ({@code numberOfParticipations}, the quiz
 * question count, and the programming {@code template}/{@code solutionParticipation} build-plan ids). Once that branch
 * merges, unification is a rename plus a field-union, not a reshape.
 *
 * @param id                             the id of the exam
 * @param title                          the title of the exam
 * @param testExam                       whether this is a test exam
 * @param examWithAttendanceCheck        whether an attendance check is enabled
 * @param visibleDate                    the date the exam becomes visible
 * @param startDate                      the exam start date
 * @param endDate                        the exam end date
 * @param publishResultsDate             the date results are published
 * @param examStudentReviewStart         the start of the student review period
 * @param examStudentReviewEnd           the end of the student review period
 * @param gracePeriod                    the grace period in seconds
 * @param workingTime                    the regular working time in seconds
 * @param startText                      the markdown start text
 * @param endText                        the markdown end text
 * @param confirmationStartText          the markdown confirmation start text
 * @param confirmationEndText            the markdown confirmation end text
 * @param examMaxPoints                  the maximum achievable points
 * @param randomizeExerciseOrder         whether the exercise order is randomized per student
 * @param numberOfExercisesInExam        the number of exercises drawn per student exam
 * @param numberOfCorrectionRoundsInExam the number of correction rounds
 * @param examiner                       the examiner
 * @param moduleNumber                   the module number
 * @param courseName                     the course name shown on the exam cover
 * @param exampleSolutionPublicationDate the date example solutions are published
 * @param course                         the slim course projection (id, title, testCourse, group names)
 * @param examArchivePath                the archive path; the exam-detail archive button reads it to enable download / cleanup
 * @param started                        whether the exam has started (computed server-side; the test-run screen reads it)
 * @param numberOfExamUsers              the number of registered exam users (transient; set by setExamProperties)
 * @param exerciseGroups                 the exercise groups with their exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamWithExerciseGroupsDTO(long id, @Nullable String title, boolean testExam, boolean examWithAttendanceCheck, @Nullable ZonedDateTime visibleDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate, @Nullable ZonedDateTime publishResultsDate, @Nullable ZonedDateTime examStudentReviewStart,
        @Nullable ZonedDateTime examStudentReviewEnd, @Nullable Integer gracePeriod, int workingTime, @Nullable String startText, @Nullable String endText,
        @Nullable String confirmationStartText, @Nullable String confirmationEndText, int examMaxPoints, @Nullable Boolean randomizeExerciseOrder,
        @Nullable Integer numberOfExercisesInExam, @Nullable Integer numberOfCorrectionRoundsInExam, @Nullable String examiner, @Nullable String moduleNumber,
        @Nullable String courseName, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable CourseForExamDTO course, @Nullable String examArchivePath, boolean started,
        @Nullable Long numberOfExamUsers, @Nullable List<ExerciseGroupWithExercisesDTO> exerciseGroups) {

    /**
     * Builds the response for the detailed {@code GET} ({@code withExerciseGroups=true}): the exercise-group management
     * screen shape, with quiz-question counts and programming build-plan ids populated per exercise.
     *
     * @param exam the fully hydrated exam
     * @return the detailed DTO
     */
    public static ExamWithExerciseGroupsDTO ofDetailed(Exam exam) {
        return of(exam, true);
    }

    /**
     * Builds the response for {@code reset}: the exam-detail screen shape. The reset fetch does not hydrate quiz
     * questions or programming participations, so those are omitted; {@code numberOfParticipations} (set by
     * {@code setExamProperties}) is still carried for the deletion summary.
     *
     * @param exam the reset exam (groups + exercises + transients, no exercise details)
     * @return the reset DTO
     */
    public static ExamWithExerciseGroupsDTO ofReset(Exam exam) {
        return of(exam, false);
    }

    private static ExamWithExerciseGroupsDTO of(Exam exam, boolean withDetails) {
        List<ExerciseGroupWithExercisesDTO> groups = exam.getExerciseGroups() == null ? null
                : exam.getExerciseGroups().stream().map(group -> ExerciseGroupWithExercisesDTO.of(group, withDetails)).toList();
        return new ExamWithExerciseGroupsDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.isExamWithAttendanceCheck(), exam.getVisibleDate(), exam.getStartDate(),
                exam.getEndDate(), exam.getPublishResultsDate(), exam.getExamStudentReviewStart(), exam.getExamStudentReviewEnd(), exam.getGracePeriod(), exam.getWorkingTime(),
                exam.getStartText(), exam.getEndText(), exam.getConfirmationStartText(), exam.getConfirmationEndText(), exam.getExamMaxPoints(), exam.getRandomizeExerciseOrder(),
                exam.getNumberOfExercisesInExam(), exam.getNumberOfCorrectionRoundsInExam(), exam.getExaminer(), exam.getModuleNumber(), exam.getCourseName(),
                exam.getExampleSolutionPublicationDate(), CourseForExamDTO.of(exam.getCourse()), exam.getExamArchivePath(), exam.isStarted(), exam.getNumberOfExamUsers(), groups);
    }

    /**
     * An exercise group with its exercises, as read by the exam-detail and exercise-group management screens.
     *
     * @param id          the id of the exercise group
     * @param title       the title of the exercise group
     * @param isMandatory whether the group must be included when generating student exams
     * @param exercises   the exercises of the group
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseGroupWithExercisesDTO(long id, @Nullable String title, @Nullable Boolean isMandatory, @Nullable List<ExamExerciseDTO> exercises) {

        static ExerciseGroupWithExercisesDTO of(ExerciseGroup group, boolean withDetails) {
            List<ExamExerciseDTO> exercises = null;
            var groupExercises = group.getExercises();
            if (Hibernate.isInitialized(groupExercises) && groupExercises != null && !groupExercises.isEmpty()) {
                exercises = groupExercises.stream().map(exercise -> ExamExerciseDTO.of(exercise, withDetails)).toList();
            }
            return new ExerciseGroupWithExercisesDTO(group.getId(), group.getTitle(), group.getIsMandatory(), exercises);
        }
    }

    /**
     * Flat per-exercise summary embedded in an exercise group, carrying the fields the exam management table and its
     * per-type cells / row-buttons read. The base scalars mirror the #13097 {@code ExerciseForExerciseGroupDTO}; the
     * exam-detail additions are {@code numberOfParticipations} (transient, deletion summary), the programming
     * {@code template}/{@code solutionParticipation} build-plan ids (programming cell), and {@code quizQuestions} (a
     * count-only projection — see below). {@code testRunParticipationsExist} and {@code numberOfParticipations} are
     * transients set by {@code setExamProperties} and are present on both the detailed and reset shapes.
     *
     * <h4>Why quizQuestions is a count-only projection</h4>
     * The only reader of {@code quizQuestions} on this payload is the quiz cell, which renders
     * {@code exercise().quizQuestions?.length}. The quiz export button does not read this payload — it re-fetches the
     * quiz via {@code QuizExerciseService.find(id)} before exporting. So the list is populated with id-only stubs, which
     * preserves the rendered length with no client change while dropping the heavy quiz-question graph the entity used
     * to serialize.
     *
     * @param id                         the id of the exercise
     * @param type                       the exercise type discriminator (e.g. "programming")
     * @param title                      the exercise title
     * @param maxPoints                  the maximum points achievable
     * @param bonusPoints                the bonus points achievable
     * @param includedInOverallScore     whether the exercise counts towards the overall score
     * @param assessmentType             the assessment mode
     * @param teamMode                   whether the exercise is a team exercise (always individual for exam exercises)
     * @param testRunParticipationsExist whether test-run participations exist (transient; quiz exercises only)
     * @param numberOfParticipations     the number of student participations (transient; deletion summary)
     * @param shortName                  the exercise short name (programming exercises)
     * @param projectKey                 the VCS/CI project key (programming exercises)
     * @param allowOfflineIde            whether the offline IDE is allowed (programming exercises)
     * @param allowOnlineEditor          whether the online editor is allowed (programming exercises)
     * @param allowOnlineIde             whether the online IDE is allowed (programming exercises)
     * @param templateParticipation      the template participation build-plan id (programming exercises, detailed only)
     * @param solutionParticipation      the solution participation build-plan id (programming exercises, detailed only)
     * @param diagramType                the UML diagram type (modeling exercises)
     * @param filePattern                the accepted file pattern (file-upload exercises)
     * @param quizQuestions              the id-only quiz-question stubs (quiz exercises, detailed only; length is read)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamExerciseDTO(long id, ExerciseType type, @Nullable String title, @Nullable Double maxPoints, @Nullable Double bonusPoints,
            @Nullable IncludedInOverallScore includedInOverallScore, @Nullable AssessmentType assessmentType, boolean teamMode, @Nullable Boolean testRunParticipationsExist,
            @Nullable Long numberOfParticipations, @Nullable String shortName, @Nullable String projectKey, @Nullable Boolean allowOfflineIde, @Nullable Boolean allowOnlineEditor,
            @Nullable Boolean allowOnlineIde, @Nullable ExamProgrammingParticipationDTO templateParticipation, @Nullable ExamProgrammingParticipationDTO solutionParticipation,
            @Nullable DiagramType diagramType, @Nullable String filePattern, @Nullable List<ExamQuizQuestionDTO> quizQuestions) {

        static ExamExerciseDTO of(Exercise exercise, boolean withDetails) {
            String projectKey = null;
            Boolean allowOfflineIde = null;
            Boolean allowOnlineEditor = null;
            Boolean allowOnlineIde = null;
            DiagramType diagramType = null;
            String filePattern = null;
            ExamProgrammingParticipationDTO templateParticipation = null;
            ExamProgrammingParticipationDTO solutionParticipation = null;
            List<ExamQuizQuestionDTO> quizQuestions = null;

            switch (exercise) {
                case ProgrammingExercise programmingExercise -> {
                    projectKey = programmingExercise.getProjectKey();
                    allowOfflineIde = programmingExercise.isAllowOfflineIde();
                    allowOnlineEditor = programmingExercise.isAllowOnlineEditor();
                    allowOnlineIde = programmingExercise.isAllowOnlineIde();
                    if (withDetails) {
                        templateParticipation = ExamProgrammingParticipationDTO.of(programmingExercise.getTemplateParticipation());
                        solutionParticipation = ExamProgrammingParticipationDTO.of(programmingExercise.getSolutionParticipation());
                    }
                }
                case ModelingExercise modelingExercise -> diagramType = modelingExercise.getDiagramType();
                case FileUploadExercise fileUploadExercise -> filePattern = fileUploadExercise.getFilePattern();
                case QuizExercise quizExercise -> {
                    if (withDetails) {
                        var questions = quizExercise.getQuizQuestions();
                        if (Hibernate.isInitialized(questions) && questions != null) {
                            quizQuestions = questions.stream().map(question -> new ExamQuizQuestionDTO(question.getId())).toList();
                        }
                    }
                }
                default -> {
                    // text exercises carry no additional type-specific columns
                }
            }

            return new ExamExerciseDTO(exercise.getId(), exercise.getExerciseType(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                    exercise.getIncludedInOverallScore(), exercise.getAssessmentType(), exercise.isTeamMode(), exercise.getTestRunParticipationsExist(),
                    exercise.getNumberOfParticipations(), exercise.getShortName(), projectKey, allowOfflineIde, allowOnlineEditor, allowOnlineIde, templateParticipation,
                    solutionParticipation, diagramType, filePattern, quizQuestions);
        }
    }

    /**
     * Slim programming participation projection carrying only the build-plan id. The exercise-group programming cell
     * renders {@code buildPlanId} directly (LocalCI) or derives the build-plan URL from it client-side (Jenkins). The
     * previously serialized latest submission / result graph is omitted: its only client use fed the
     * {@code numberOfResultsOf{Template,Solution}Participation} signals, which are computed but never rendered on this
     * screen, so dropping it changes nothing observable.
     *
     * @param buildPlanId the CI build-plan id of the participation
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamProgrammingParticipationDTO(@Nullable String buildPlanId) {

        @Nullable
        static ExamProgrammingParticipationDTO of(@Nullable ProgrammingExerciseParticipation participation) {
            if (!Hibernate.isInitialized(participation) || participation == null) {
                return null;
            }
            return new ExamProgrammingParticipationDTO(participation.getBuildPlanId());
        }
    }

    /**
     * Id-only quiz-question stub. The client reads only {@code quizQuestions.length} off this payload, so no further
     * question fields are serialized.
     *
     * @param id the id of the quiz question
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamQuizQuestionDTO(long id) {
    }
}
