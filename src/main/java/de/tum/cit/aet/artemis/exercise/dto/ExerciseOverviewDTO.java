package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * An exercise as the course overview renders it: the exercise card, its filters, the statistics charts and the variant
 * group grouping, and nothing else.
 * <p>
 * The entity carries a long tail of fields no overview consumer reads — the whole programming configuration among them
 * (project key and type, package name, programming language, build and test settings, online editor flags, static code
 * analysis) — and that tail grows with how richly an exercise is configured. Measured on a course of ten programming and
 * ten text exercises, a third of the exercise payload was fields nothing rendered.
 *
 * @param type                                       the exercise kind; the client discriminates on this and picks the icon from it
 * @param id                                         the id of the exercise
 * @param title                                      the title shown on the card
 * @param maxPoints                                  the attainable points, used by the card filter and the statistics charts
 * @param bonusPoints                                the attainable bonus points
 * @param releaseDate                                when the exercise was released, used for the date grouping
 * @param startDate                                  when work on the exercise may start, used for the date grouping
 * @param dueDate                                    the submission deadline, shown as the card subtitle and used for sorting
 * @param assessmentDueDate                          when assessment closes; the result display decides from it whether a result is final
 * @param assessmentType                             how the exercise is assessed; the programming result display branches on it
 * @param difficulty                                 shown on the card and filterable
 * @param mode                                       whether the exercise is worked on individually or in a team
 * @param teamMode                                   convenience flag derived from the mode, which the card reads directly
 * @param includedInOverallScore                     how the exercise counts, needed by the statistics charts
 * @param categories                                 the exercise categories, filterable on the card
 * @param presentationScoreEnabled                   whether presentation points apply, needed by the statistics charts
 * @param allowFeedbackRequests                      whether the student may request automatic feedback from the exercise card
 * @param allowOnlineEditor                          whether a programming exercise can be opened in the online editor
 * @param allowOfflineIde                            whether a programming exercise can be cloned and worked on locally
 * @param staticCodeAnalysisEnabled                  whether a programming exercise reports static code analysis issues, which gates the code-issue counter
 *                                                       shown on the exercise header
 * @param allowOnlineIde                             whether a programming exercise can be opened in the online IDE, read by the code button. This and the four
 *                                                       programming fields below are only populated when the exercise is projected from the entity (lecture exercise
 *                                                       units, whose row also opens the result and feedback views); the course overview query leaves them out on
 *                                                       purpose (see CourseOverviewLoadProfileTest)
 * @param programmingLanguage                        the programming language, which picks the user's preferred IDE on the code button
 * @param buildAndTestStudentSubmissionsAfterDueDate when the final tests of a programming exercise run; a result before that date is shown as preliminary
 * @param showTestNamesToStudents                    whether the feedback view may show test names to students
 * @param maxStaticCodeAnalysisPenalty               the static code analysis penalty cap the feedback view displays
 * @param quizEnded                                  whether a quiz has ended, used by its action and result status
 * @param quizBatches                                a single started marker when the requesting student's relevant batch has begun
 * @param studentAssignedTeamId                      the team the user belongs to, for team exercises
 * @param studentAssignedTeamIdComputed              whether the assigned team was resolved, so the card can tell "no team" from
 *                                                       "not looked up"
 * @param exerciseVariantGroup                       the variant group this exercise belongs to, which drives the grouped card
 * @param studentParticipations                      the user's participations with their submissions and results
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseOverviewDTO(ExerciseType type, Long id, String title, Double maxPoints, Double bonusPoints, ZonedDateTime releaseDate, ZonedDateTime startDate,
        ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, AssessmentType assessmentType, DifficultyLevel difficulty, ExerciseMode mode, boolean teamMode,
        IncludedInOverallScore includedInOverallScore, Set<String> categories, Boolean presentationScoreEnabled, boolean allowFeedbackRequests, Boolean allowOnlineEditor,
        Boolean allowOfflineIde, Boolean staticCodeAnalysisEnabled, Boolean allowOnlineIde, ProgrammingLanguage programmingLanguage,
        ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, Boolean showTestNamesToStudents, Integer maxStaticCodeAnalysisPenalty, Boolean quizEnded,
        Set<QuizBatchOverviewDTO> quizBatches, Long studentAssignedTeamId, boolean studentAssignedTeamIdComputed, ExerciseVariantGroupReferenceDTO exerciseVariantGroup,
        Set<ParticipationOverviewDTO> studentParticipations) {

    /**
     * Projects an exercise, together with the user's participations, for the course overview.
     *
     * @param exercise the exercise to project
     * @return the projected exercise
     */
    public static ExerciseOverviewDTO of(Exercise exercise) {
        ProgrammingExercise programmingExercise = exercise instanceof ProgrammingExercise programming ? programming : null;
        QuizExercise quizExercise = exercise instanceof QuizExercise quiz ? quiz : null;
        Set<String> categories = Hibernate.isInitialized(exercise.getCategories()) ? exercise.getCategories() : Set.of();
        Set<ParticipationOverviewDTO> studentParticipations = Hibernate.isInitialized(exercise.getStudentParticipations())
                ? ParticipationOverviewDTO.of(exercise.getStudentParticipations())
                : Set.of();
        Set<QuizBatchOverviewDTO> quizBatches = quizExercise != null && Hibernate.isInitialized(quizExercise.getQuizBatches()) && quizExercise.getQuizBatches() != null
                && quizExercise.getQuizBatches().stream().anyMatch(batch -> batch.isStarted()) ? Set.of(QuizBatchOverviewDTO.STARTED) : Set.of();
        return new ExerciseOverviewDTO(exercise.getExerciseType(), exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getAssessmentType(), exercise.getDifficulty(),
                exercise.getMode(), exercise.isTeamMode(), exercise.getIncludedInOverallScore(), categories, exercise.getPresentationScoreEnabled(),
                exercise.getAllowFeedbackRequests(), programmingExercise == null ? null : programmingExercise.isAllowOnlineEditor(),
                programmingExercise == null ? null : programmingExercise.isAllowOfflineIde(),
                programmingExercise == null ? null : programmingExercise.isStaticCodeAnalysisEnabled(), programmingExercise == null ? null : programmingExercise.isAllowOnlineIde(),
                programmingExercise == null ? null : programmingExercise.getProgrammingLanguage(),
                programmingExercise == null ? null : programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate(),
                programmingExercise == null ? null : programmingExercise.getShowTestNamesToStudents(),
                programmingExercise == null ? null : programmingExercise.getMaxStaticCodeAnalysisPenalty(), quizExercise == null ? null : quizExercise.isQuizEnded(), quizBatches,
                exercise.getStudentAssignedTeamId(), exercise.isStudentAssignedTeamIdComputed(), ExerciseVariantGroupReferenceDTO.ofNullable(exercise.getExerciseVariantGroup()),
                studentParticipations);
    }

    /**
     * Projects the exercises of a course for the course overview.
     *
     * @param exercises the exercises to project
     * @return the projected exercises
     */
    public static Set<ExerciseOverviewDTO> of(Set<Exercise> exercises) {
        return exercises == null ? Set.of() : exercises.stream().map(ExerciseOverviewDTO::of).collect(Collectors.toSet());
    }
}
