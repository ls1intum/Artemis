package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

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
 * {@code exercise.numberOfParticipations}, both present via {@code setExamProperties}. Every lazy sub-relation the
 * factories touch — the exercise groups, each group's exercises, quiz questions and programming participations — is read
 * behind the same {@code Hibernate.isInitialized(x) && x != null} guard, so a factory never forces a load outside the
 * transaction.
 * <p>
 * TODO (accepted follow-up, tracked separately): the detailed fetch still over-fetches the full quiz-question and
 * programming-participation graphs that only feed the slim {@link ExamQuizQuestionDTO} / {@link ExamProgrammingParticipationDTO}
 * projections here. Slimming that fetch is a server-side perf change deferred until these DTO wire shapes lock.
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
 * @param started                        whether the exam has started (computed server-side; {@code false} when no start date is set; the test-run screen reads it)
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
        @Nullable Long numberOfExamUsers, @Nullable List<ExerciseGroupWithExercisesDTO> exerciseGroups) implements ExamResponseDTO {

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

    /**
     * Builds a row for the exam-management list ({@code GET courses/{courseId}/exams},
     * {@link de.tum.cit.aet.artemis.exam.web.ExamResource#getExamsForCourse}). That screen renders each exam's status
     * via the {@code exam-status} component + {@code ExamChecklistService}, which read the scalar core plus, off the
     * exercise groups, only {@code exerciseGroup.isMandatory} and {@code exerciseGroup.exercises[*].maxPoints}, together
     * with {@code numberOfExamUsers} (set by {@code setNumberOfExamUsersForExams}) and {@code numberOfExercisesInExam}.
     * The fetch behind this endpoint hydrates neither quiz questions nor programming participations, so — exactly like
     * {@link #ofReset(Exam)} — the {@code withDetails=false} shape applies (those per-exercise projections stay null and
     * no lazy load is forced). All the fields those readers touch are a subset of what this shape already carries.
     *
     * @param exam an exam of the course (groups + exercises + {@code numberOfExamUsers}, no exercise details)
     * @return the exam-management list row
     */
    public static ExamWithExerciseGroupsDTO ofExamManagementList(Exam exam) {
        return of(exam, false);
    }

    /**
     * Builds the response for the exam-import fetch ({@code GET exams/{examId}},
     * {@link de.tum.cit.aet.artemis.exam.web.ExamResource#getExamForImportWithExercises}). That fetch hydrates the same
     * fully detailed graph as the detailed {@code GET} ({@code findByIdWithExerciseGroupsAndExercisesElseThrow(examId, true)}),
     * so it uses the {@code withDetails=true} shape.
     * <p>
     * Two client consumers read this response and echo it back onto write paths, and this shape serves both:
     * <ul>
     * <li>the exercise-group import modal ({@code exam-exercise-import.component}) renders per exercise {@code id}, {@code type},
     * {@code title}, {@code shortName}, {@code difficulty} and per group {@code id}, {@code title}, {@code isMandatory}, then
     * re-posts the selected groups to {@code import-exercise-group} — where the exercises deserialize back into the polymorphic
     * {@code Exercise} hierarchy (and their quiz-question / programming-participation stubs), so the {@code type} discriminators
     * this shape carries are load-bearing for that echo. For quiz exercises the posted skeleton is also the source
     * {@code QuizExerciseImportService#copyQuizExerciseBasis} reads {@code randomizeQuestionOrder}, {@code allowedNumberOfAttempts},
     * {@code quizMode} and {@code duration} from, so {@link ExamExerciseDTO} carries those four scalars purely for this echo — no
     * screen renders them, but their absence would silently reset a customized quiz's configuration on import;</li>
     * <li>the create-exam-from-import editor resolves this response ({@code ExamResolve} with {@code forImport}) into
     * {@code exam-update.component} — the same component and therefore the same field set as the normal edit route, which already
     * consumes this DTO — and its body-builder ({@code convertExamToImportDTO} / {@code convertExerciseGroupsToImportDTO}) reads
     * the scalar exam core plus, per exercise, {@code id}, {@code type}, {@code title}, {@code shortName}, {@code maxPoints},
     * {@code bonusPoints} and, per group, {@code title}, {@code isMandatory} — all a subset of this shape.</li>
     * </ul>
     *
     * @param exam the fully hydrated exam (groups + exercises with quiz-question / programming details)
     * @return the exam-import DTO
     */
    public static ExamWithExerciseGroupsDTO ofImport(Exam exam) {
        return of(exam, true);
    }

    private static ExamWithExerciseGroupsDTO of(Exam exam, boolean withDetails) {
        // Single source for the shared scalar core: build the plain ExamDTO once and copy its accessors, so only ExamDTO.of
        // reads the scalar entity getters. channelName (ExamDTO-only) is dropped; started and numberOfExamUsers are added.
        ExamDTO core = ExamDTO.of(exam);
        var exerciseGroups = exam.getExerciseGroups();
        List<ExerciseGroupWithExercisesDTO> groups = Hibernate.isInitialized(exerciseGroups) && exerciseGroups != null
                ? exerciseGroups.stream().map(group -> ExerciseGroupWithExercisesDTO.of(group, withDetails)).toList()
                : null;
        // isStarted() dereferences startDate, so guard the null case: a not-yet-scheduled exam reads as not started.
        boolean started = exam.getStartDate() != null && exam.isStarted();
        return new ExamWithExerciseGroupsDTO(core.id(), core.title(), core.testExam(), core.examWithAttendanceCheck(), core.visibleDate(), core.startDate(), core.endDate(),
                core.publishResultsDate(), core.examStudentReviewStart(), core.examStudentReviewEnd(), core.gracePeriod(), core.workingTime(), core.startText(), core.endText(),
                core.confirmationStartText(), core.confirmationEndText(), core.examMaxPoints(), core.randomizeExerciseOrder(), core.numberOfExercisesInExam(),
                core.numberOfCorrectionRoundsInExam(), core.examiner(), core.moduleNumber(), core.courseName(), core.exampleSolutionPublicationDate(), core.course(),
                core.examArchivePath(), started, exam.getNumberOfExamUsers(), groups);
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
            if (Hibernate.isInitialized(groupExercises) && groupExercises != null) {
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
     * @param difficulty                 the difficulty level (the create-test-run modal renders this cell)
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
     * @param quizQuestions              the id+type quiz-question stubs (quiz exercises, detailed only; length is read, type kept so client echoes round-trip)
     * @param randomizeQuestionOrder     whether the question order is randomized (quiz exercises, detailed only); present purely so the
     *                                       group-import echo carries it — see "Why the quiz-config scalars are present" below
     * @param allowedNumberOfAttempts    the number of allowed attempts (quiz exercises, detailed only); import-echo only, see below
     * @param quizMode                   the quiz participation mode (quiz exercises, detailed only); import-echo only, see below
     * @param duration                   the quiz duration in seconds (quiz exercises, detailed only); import-echo only, see below
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamExerciseDTO(long id, ExerciseType type, @Nullable String title, @Nullable Double maxPoints, @Nullable Double bonusPoints,
            @Nullable DifficultyLevel difficulty, @Nullable IncludedInOverallScore includedInOverallScore, @Nullable AssessmentType assessmentType, boolean teamMode,
            @Nullable Boolean testRunParticipationsExist, @Nullable Long numberOfParticipations, @Nullable String shortName, @Nullable String projectKey,
            @Nullable Boolean allowOfflineIde, @Nullable Boolean allowOnlineEditor, @Nullable Boolean allowOnlineIde,
            @Nullable ExamProgrammingParticipationDTO templateParticipation, @Nullable ExamProgrammingParticipationDTO solutionParticipation, @Nullable DiagramType diagramType,
            @Nullable String filePattern, @Nullable List<ExamQuizQuestionDTO> quizQuestions, @Nullable Boolean randomizeQuestionOrder, @Nullable Integer allowedNumberOfAttempts,
            @Nullable QuizMode quizMode, @Nullable Integer duration) {

        /*
         * Why the quiz-config scalars are present: the exercise-group import modal re-posts this exact exercise object to
         * import-exercise-group, where it deserializes back into a QuizExercise skeleton and is passed as the
         * `importedExercise` argument of QuizExerciseImportService#importQuizExercise. copyQuizExerciseBasis reads
         * randomizeQuestionOrder, allowedNumberOfAttempts, quizMode and duration off exactly that skeleton to build the
         * persisted copy. Without them here, a customized (e.g. batched, multi-attempt) quiz would silently import with
         * default/null configuration. These four fields are therefore load-bearing for the import echo even though no
         * screen renders them directly; do not remove them without checking copyQuizExerciseBasis still doesn't need them.
         */
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
            Boolean randomizeQuestionOrder = null;
            Integer allowedNumberOfAttempts = null;
            QuizMode quizMode = null;
            Integer duration = null;

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
                            quizQuestions = questions.stream().map(ExamQuizQuestionDTO::of).toList();
                        }
                        // Scalars the group-import echo needs (see class-level comment above); every quiz column, no entity graph.
                        randomizeQuestionOrder = quizExercise.isRandomizeQuestionOrder();
                        allowedNumberOfAttempts = quizExercise.getAllowedNumberOfAttempts();
                        quizMode = quizExercise.getQuizMode();
                        duration = quizExercise.getDuration();
                    }
                }
                default -> {
                    // text exercises carry no additional type-specific columns
                }
            }

            return new ExamExerciseDTO(exercise.getId(), exercise.getExerciseType(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                    exercise.getDifficulty(), exercise.getIncludedInOverallScore(), exercise.getAssessmentType(), exercise.isTeamMode(), exercise.getTestRunParticipationsExist(),
                    exercise.getNumberOfParticipations(), exercise.getShortName(), projectKey, allowOfflineIde, allowOnlineEditor, allowOnlineIde, templateParticipation,
                    solutionParticipation, diagramType, filePattern, quizQuestions, randomizeQuestionOrder, allowedNumberOfAttempts, quizMode, duration);
        }
    }

    /**
     * Slim programming participation projection carrying the build-plan id plus the polymorphic type discriminator. The
     * exercise-group programming cell renders {@code buildPlanId} directly (LocalCI) or derives the build-plan URL from it
     * client-side (Jenkins). The previously serialized latest submission / result graph is omitted: its only client use
     * fed the {@code numberOfResultsOf{Template,Solution}Participation} signals, which are computed but never rendered on
     * this screen, so dropping it changes nothing observable.
     * <p>
     * Like {@link ExamQuizQuestionDTO}, the stub deliberately carries the {@code type} discriminator because the client
     * echoes this graph back on the write paths (create-test-run POST and exercise-groups-order PUT). On the wire
     * {@link de.tum.cit.aet.artemis.exercise.domain.participation.Participation} is
     * {@code @JsonTypeInfo(use = NAME, property = "type")}; the {@code templateParticipation} / {@code solutionParticipation}
     * fields deserialize into the concrete {@code Template}/{@code Solution} participation types, so a stub without
     * {@code type} fails polymorphic deserialization with a 400. {@code type} emits exactly the entity's
     * {@code @JsonSubTypes} names ({@code "template"} / {@code "solution"}) so those echoes round-trip.
     *
     * @param type        the polymorphic discriminator matching {@code Participation}'s {@code @JsonSubTypes} name
     * @param buildPlanId the CI build-plan id of the participation
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamProgrammingParticipationDTO(String type, @Nullable String buildPlanId) {

        @Nullable
        static ExamProgrammingParticipationDTO of(@Nullable ProgrammingExerciseParticipation participation) {
            if (Hibernate.isInitialized(participation) && participation != null) {
                return new ExamProgrammingParticipationDTO(typeOf(participation), participation.getBuildPlanId());
            }
            return null;
        }

        /**
         * Maps a concrete programming participation to the wire name declared in {@code Participation}'s
         * {@code @JsonSubTypes}, so an echoed stub deserializes back into the same subtype.
         */
        private static String typeOf(ProgrammingExerciseParticipation participation) {
            return switch (participation) {
                case TemplateProgrammingExerciseParticipation ignored -> "template";
                case SolutionProgrammingExerciseParticipation ignored -> "solution";
                default -> throw new IllegalArgumentException("Unsupported programming participation type: " + participation.getClass().getName());
            };
        }
    }

    /**
     * Slim quiz-question stub carrying id + polymorphic type discriminator. The exercise-group quiz cell reads only
     * {@code quizQuestions.length}, so no further question fields are serialized. The stub deliberately carries the
     * {@code type} discriminator (not just the id) because the client echoes this graph back on write paths — the
     * create-test-run POST ({@link de.tum.cit.aet.artemis.exam.web.StudentExamResource#createTestRun} takes a
     * {@code StudentExam} whose exercises are these DTO objects) and the exercise-groups-order PUT
     * ({@link de.tum.cit.aet.artemis.exam.web.ExamResource#updateOrderOfExerciseGroups} takes the echoed
     * {@code ExerciseGroup}s). On the wire {@link QuizQuestion} is {@code @JsonTypeInfo(use = NAME, property = "type")};
     * an id-only stub without {@code type} fails polymorphic deserialization with a 400. {@code type} therefore emits
     * exactly the entity's {@code @JsonSubTypes} names so those echoes round-trip.
     *
     * @param id   the id of the quiz question
     * @param type the polymorphic discriminator matching {@link QuizQuestion}'s {@code @JsonSubTypes} name
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamQuizQuestionDTO(long id, String type) {

        static ExamQuizQuestionDTO of(QuizQuestion question) {
            return new ExamQuizQuestionDTO(question.getId(), typeOf(question));
        }

        /**
         * Maps a concrete quiz question to the wire name declared in {@link QuizQuestion}'s {@code @JsonSubTypes}, so an
         * echoed stub deserializes back into the same subtype.
         */
        private static String typeOf(QuizQuestion question) {
            return switch (question) {
                case MultipleChoiceQuestion ignored -> "multiple-choice";
                case DragAndDropQuestion ignored -> "drag-and-drop";
                case ShortAnswerQuestion ignored -> "short-answer";
                default -> throw new IllegalArgumentException("Unknown quiz question type: " + question.getClass().getName());
            };
        }
    }
}
