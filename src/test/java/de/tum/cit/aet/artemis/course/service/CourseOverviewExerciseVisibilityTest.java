package de.tum.cit.aet.artemis.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseForCourseOverviewDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationOverviewRowDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationResultDTO;
import de.tum.cit.aet.artemis.exercise.dto.ResultOverviewDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;

/** Tests the student-visible result boundary through the assembled course overview. */
class CourseOverviewExerciseVisibilityTest {

    private static final ZonedDateTime PAST = ZonedDateTime.parse("2020-01-01T00:00:00Z");

    private static final ZonedDateTime FUTURE = ZonedDateTime.parse("2100-01-01T00:00:00Z");

    private final ExerciseTestRepository exercises = mock(ExerciseTestRepository.class);

    private final StudentParticipationTestRepository participations = mock(StudentParticipationTestRepository.class);

    private final TeamRepository teams = mock(TeamRepository.class);

    private final GradingScaleRepository grading = mock(GradingScaleRepository.class);

    private final AuthorizationCheckService authorization = mock(AuthorizationCheckService.class);

    private final PlagiarismCaseApi plagiarism = mock(PlagiarismCaseApi.class);

    private final CourseOverviewExerciseService service = new CourseOverviewExerciseService(exercises, participations, teams, grading, authorization, Optional.of(plagiarism));

    private Course course;

    private User student;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setId(1L);
        course.setAccuracyOfScores(1);
        student = new User();
        student.setId(2L);
        student.setLogin("student");
    }

    @ParameterizedTest
    @MethodSource("visibilityCases")
    void exposesOnlyEligibleSubmissionsAndResults(ExerciseType type, ZonedDateTime due, ZonedDateTime assessmentDue, ZonedDateTime individualDue, Boolean submitted,
            ZonedDateTime submittedAt, Long resultId, Boolean rated, ZonedDateTime completed, AssessmentType assessmentType, boolean visible, boolean resultVisible) {
        var exercise = exercise(type, due, assessmentDue);
        var row = row(10L, submittedAt, submitted, individualDue, resultId, rated, completed, assessmentType);
        prepare(exercise, List.of(row));

        var response = service.getCourseExercisesForOverview(course, student);

        assertThat(response.exercises()).hasSize(1);
        var participation = response.exercises().iterator().next().studentParticipations().iterator().next();
        assertThat(participation.id()).isEqualTo(3L);
        assertThat(participation.submissions()).hasSize(visible ? 1 : 0);
        if (visible) {
            var submission = participation.submissions().iterator().next();
            assertThat(submission.id()).isEqualTo(10L);
            assertThat(submission.submissionExerciseType()).isEqualTo(type.getValue());
            assertThat(submission.results()).hasSize(resultVisible ? 1 : 0);
            if (resultVisible) {
                assertThat(submission.results().getFirst().id()).isEqualTo(resultId);
                assertThat(submission.results().getFirst().score()).isEqualTo(80.0);
            }
        }
        assertThat(response.participationResults()).containsExactly(new ParticipationResultDTO(0.0, false, 3L));
    }

    private static Stream<Arguments> visibilityCases() {
        return Stream.of(Arguments.of(ExerciseType.QUIZ, null, null, null, true, PAST, null, null, null, null, true, false),
                Arguments.of(ExerciseType.QUIZ, FUTURE, null, null, false, PAST, null, null, null, null, false, false),
                Arguments.of(ExerciseType.QUIZ, FUTURE, null, null, null, PAST, null, null, null, null, false, false),
                Arguments.of(ExerciseType.QUIZ, PAST, null, null, true, PAST, null, null, null, null, false, false),
                Arguments.of(ExerciseType.QUIZ, PAST, null, null, true, PAST, 20L, false, PAST, AssessmentType.AUTOMATIC, false, false),
                Arguments.of(ExerciseType.QUIZ, PAST, null, null, true, PAST, 20L, true, null, AssessmentType.AUTOMATIC, false, false),
                Arguments.of(ExerciseType.QUIZ, PAST, null, null, true, PAST, 20L, true, PAST, AssessmentType.AUTOMATIC, true, true),
                Arguments.of(ExerciseType.PROGRAMMING, null, null, null, true, null, null, null, null, null, true, false),
                Arguments.of(ExerciseType.PROGRAMMING, PAST, null, FUTURE, true, PAST, null, null, null, null, true, false),
                Arguments.of(ExerciseType.PROGRAMMING, PAST, null, null, true, null, null, null, null, null, false, false),
                Arguments.of(ExerciseType.PROGRAMMING, PAST, null, null, true, FUTURE, null, null, null, null, false, false),
                Arguments.of(ExerciseType.PROGRAMMING, PAST, FUTURE, null, true, PAST, 20L, true, PAST, AssessmentType.MANUAL, false, false),
                Arguments.of(ExerciseType.PROGRAMMING, PAST, null, null, true, PAST, 20L, true, null, AssessmentType.SEMI_AUTOMATIC, false, false),
                Arguments.of(ExerciseType.PROGRAMMING, PAST, PAST, null, true, PAST, 20L, true, PAST, AssessmentType.MANUAL, true, true),
                Arguments.of(ExerciseType.PROGRAMMING, PAST, FUTURE, null, true, PAST, 20L, true, null, AssessmentType.AUTOMATIC_ATHENA, true, true),
                Arguments.of(ExerciseType.TEXT, PAST, FUTURE, null, true, PAST, 20L, true, PAST, AssessmentType.MANUAL, false, false),
                Arguments.of(ExerciseType.TEXT, PAST, null, null, true, PAST, 20L, null, PAST, AssessmentType.MANUAL, false, false),
                Arguments.of(ExerciseType.TEXT, PAST, null, null, true, PAST, 20L, true, PAST, AssessmentType.MANUAL, true, true));
    }

    @Test
    void fallsBackToLatestAutomaticAssessmentUntilManualResultsAreReleased() {
        prepare(exercise(ExerciseType.PROGRAMMING, PAST, FUTURE), List.of(row(10L, PAST, true, null, 21L, true, PAST, AssessmentType.MANUAL),
                row(10L, PAST, true, null, 19L, true, PAST, AssessmentType.AUTOMATIC), row(10L, PAST, true, null, 20L, true, PAST, AssessmentType.AUTOMATIC_ATHENA)));

        var response = service.getCourseExercisesForOverview(course, student);

        // The AI feedback only copies a test score, so the test result is the one shown; the AI feedback is sent along with it
        var submission = response.exercises().iterator().next().studentParticipations().iterator().next().submissions().iterator().next();
        assertThat(submission.results()).extracting(ResultOverviewDTO::id).containsExactly(19L, 20L);
    }

    @Test
    void sendsTheTestResultAlongWithALaterAiFeedbackOfAnAutomaticallyAssessedExercise() {
        prepare(exercise(ExerciseType.PROGRAMMING, null, null),
                List.of(row(10L, PAST, true, null, 19L, true, PAST, AssessmentType.AUTOMATIC), row(10L, PAST, true, null, 20L, true, PAST, AssessmentType.AUTOMATIC_ATHENA)));

        var response = service.getCourseExercisesForOverview(course, student);

        // An exercise group card grades by the test result, so it must not get the AI feedback alone
        var submission = response.exercises().iterator().next().studentParticipations().iterator().next().submissions().iterator().next();
        assertThat(submission.results()).extracting(ResultOverviewDTO::id, ResultOverviewDTO::assessmentType).containsExactly(tuple(19L, AssessmentType.AUTOMATIC),
                tuple(20L, AssessmentType.AUTOMATIC_ATHENA));
    }

    @ParameterizedTest
    @MethodSource("submissionDates")
    void selectsLatestSubmissionByDateAndUsesIdForEqualOrMissingDates(ZonedDateTime firstDate, ZonedDateTime secondDate, long expectedId) {
        prepare(exercise(ExerciseType.PROGRAMMING, null, null),
                List.of(row(10L, firstDate, true, null, null, null, null, null), row(11L, secondDate, true, null, null, null, null, null)));

        var response = service.getCourseExercisesForOverview(course, student);

        assertThat(response.exercises().iterator().next().studentParticipations().iterator().next().submissions()).singleElement()
                .satisfies(submission -> assertThat(submission.id()).isEqualTo(expectedId));
    }

    private static Stream<Arguments> submissionDates() {
        return Stream.of(Arguments.of(null, PAST, 11L), Arguments.of(PAST, null, 11L), Arguments.of(PAST, PAST, 11L), Arguments.of(FUTURE, PAST, 10L),
                Arguments.of(PAST, FUTURE, 11L));
    }

    @ParameterizedTest
    @CsvSource({ "false, false, false", "true, false, true", "true, true, false", "false, true, false" })
    void restrictsUnreleasedAndLtiExercisesForAnEmptyCourse(boolean online, boolean teachingAssistant, boolean requireLti) {
        course.setOnlineCourse(online);
        when(authorization.isAtLeastTeachingAssistantInCourse(course, student)).thenReturn(teachingAssistant);
        when(exercises.findForCourseOverview(eq(1L), any(), eq(teachingAssistant), eq(requireLti), eq("student"))).thenReturn(List.of());

        var overview = service.getCourseExercisesForOverview(course, student);

        assertThat(overview.exercises()).isEmpty();
        assertThat(overview.participationResults()).isEmpty();
        assertThat(overview.achievedPointsPerVariantGroup()).isEmpty();
        assertThat(overview.totalScores().maxPoints()).isZero();
        assertThat(overview.totalScores().reachablePoints()).isZero();
        verify(exercises).findForCourseOverview(eq(1L), any(), eq(teachingAssistant), eq(requireLti), eq("student"));
        verifyNoInteractions(participations, teams, grading, plagiarism);
    }

    private void prepare(ExerciseForCourseOverviewDTO exercise, List<ParticipationOverviewRowDTO> rows) {
        when(exercises.findForCourseOverview(eq(1L), any(), eq(false), eq(false), eq("student"))).thenReturn(List.of(exercise));
        when(participations.findIndividualRowsForCourseOverview(2L, Set.of(7L), true)).thenReturn(rows);
    }

    private static ExerciseForCourseOverviewDTO exercise(ExerciseType type, ZonedDateTime due, ZonedDateTime assessmentDue) {
        return new ExerciseForCourseOverviewDTO(type, 7L, "Exercise", 100.0, 0.0, PAST, PAST, due, assessmentDue, AssessmentType.AUTOMATIC, null, ExerciseMode.INDIVIDUAL,
                IncludedInOverallScore.INCLUDED_COMPLETELY, false, true, true, true, false, null, null, null, null, null, null, null, null, null);
    }

    private static ParticipationOverviewRowDTO row(long submissionId, ZonedDateTime submittedAt, Boolean submitted, ZonedDateTime individualDue, Long resultId, Boolean rated,
            ZonedDateTime completed, AssessmentType assessmentType) {
        return new ParticipationOverviewRowDTO(7L, 3L, "student", InitializationState.INITIALIZED, PAST, false, individualDue, null, null, submissionId, submittedAt, submitted,
                SubmissionType.MANUAL, false, resultId, completed, 80.0, rated, true, assessmentType, 0, 10, 8);
    }
}
