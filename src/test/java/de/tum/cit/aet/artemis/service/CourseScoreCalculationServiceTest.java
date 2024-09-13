package de.tum.cit.aet.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.BonusSourceResultDTO;
import de.tum.cit.aet.artemis.assessment.dto.MaxAndReachablePoints;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoresDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.CourseScoreCalculationService;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleFactory;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.core.dto.CourseScoresDTO;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseScoreCalculationServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "cscservicetest";

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private CourseScoreCalculationService courseScoreCalculationService;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 1);
        course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoresForExamBonusSourceWithNotIncludedExercises() {
        var exerciseList = new ArrayList<>(course.getExercises());
        exerciseList.sort(Comparator.comparing(Exercise::getId));

        var exercise = exerciseList.getFirst();
        exercise.setDueDate(null);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);

        // Other exercises should have due dates in the future which is also not included.
        ZonedDateTime now = ZonedDateTime.now();
        exerciseList.stream().skip(1).forEach(ex -> assertThat(ex.getDueDate()).isAfter(now));

        exerciseRepository.save(exercise);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        Map<Long, BonusSourceResultDTO> bonusSourceResultDTOMap = courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course, null, List.of(student.getId()));
        assertThat(bonusSourceResultDTOMap).hasSize(1);
        BonusSourceResultDTO bonusSourceResultDTO = bonusSourceResultDTOMap.get(student.getId());
        assertThat(bonusSourceResultDTO.achievedPoints()).isZero();
        assertThat(bonusSourceResultDTO.presentationScoreThreshold()).isEqualTo(course.getPresentationScore());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoreForExamBonusSourceWithoutExercises() {
        Course course = courseUtilService.addEmptyCourse();

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var courseResult = courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course, null, List.of(student.getId()));
        assertThat(courseResult).isNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoreForExamBonusSourceWithMultipleResultsInParticipation(boolean withDueDate) {

        ZonedDateTime dueDate = withDueDate ? ZonedDateTime.now() : null;
        course.getExercises().forEach(ex -> ex.setDueDate(dueDate));

        exerciseRepository.saveAll(course.getExercises());

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        List<StudentParticipation> studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        assertThat(studentParticipations).isNotEmpty();

        // Test with multiple results to assert they are sorted.
        StudentParticipation studentParticipation = studentParticipations.getFirst();
        participationUtilService.createSubmissionAndResult(studentParticipation, 50, true);
        participationUtilService.createSubmissionAndResult(studentParticipation, 40, true);
        participationUtilService.createSubmissionAndResult(studentParticipation, 60, true);

        studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        // Test with null result set.
        Set<Result> results = studentParticipations.get(1).getResults();
        resultRepository.deleteAll(results);

        // Test with empty result set.
        studentParticipations.get(2).setResults(Collections.emptySet());
        resultRepository.saveAll(studentParticipations.get(2).getResults());

        // Test with null score in result.

        // QuizExercise is selected because it has already a score of 0 in the initial test data and we have one participation for each exercise type.
        // Besides that, exercise type is irrelevant for this test.
        StudentParticipation studentParticipationWithZeroScore = studentParticipations.stream().filter(participation -> participation.getExercise() instanceof QuizExercise)
                .findFirst().orElseThrow();
        Result result = studentParticipationWithZeroScore.getResults().iterator().next();
        assertThat(result.getScore()).isZero();
        result.score(null);

        StudentScoresDTO studentScoresDTO = courseScoreCalculationService.calculateCourseScoreForStudent(course, null, student.getId(), studentParticipations,
                new MaxAndReachablePoints(25.0, 5.0, 0.0), List.of());
        if (withDueDate) {
            assertThat(studentScoresDTO.absoluteScore()).isEqualTo(2.1);
            assertThat(studentScoresDTO.relativeScore()).isEqualTo(8.4);
            assertThat(studentScoresDTO.currentRelativeScore()).isEqualTo(42.0);
        }
        else {
            assertThat(studentScoresDTO.absoluteScore()).isEqualTo(4.6);
            assertThat(studentScoresDTO.relativeScore()).isEqualTo(18.4);
            assertThat(studentScoresDTO.currentRelativeScore()).isEqualTo(92.0);
        }

        Map<Long, BonusSourceResultDTO> bonusSourceResultDTOMap = courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course, null, List.of(student.getId()));

        assertThat(bonusSourceResultDTOMap).hasSize(1);
        BonusSourceResultDTO bonusSourceResultDTO = bonusSourceResultDTOMap.get(student.getId());
        assertThat(bonusSourceResultDTO.achievedPoints()).isZero();
        assertThat(bonusSourceResultDTO.achievedPresentationScore()).isZero();
        assertThat(bonusSourceResultDTO.mostSeverePlagiarismVerdict()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getScoresAndParticipationResultsWithNotIncludedExercise() {
        var exerciseList = new ArrayList<>(course.getExercises());
        exerciseList.sort(Comparator.comparing(Exercise::getId));

        var exercise = exerciseList.getFirst();
        exercise.setDueDate(null);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);

        exerciseRepository.save(exercise);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        CourseForDashboardDTO courseForDashboard = courseScoreCalculationService.getScoresAndParticipationResults(course, null, student.getId());
        assertThat(courseForDashboard.course()).isEqualTo(course);
        CourseScoresDTO totalCourseScores = courseForDashboard.totalScores();
        assertThat(totalCourseScores.maxPoints()).isZero();
        assertThat(totalCourseScores.reachablePoints()).isZero();
        assertThat(totalCourseScores.reachablePresentationPoints()).isZero();
        assertThat(totalCourseScores.studentScores().absoluteScore()).isZero();
        assertThat(totalCourseScores.studentScores().relativeScore()).isZero();
        assertThat(totalCourseScores.studentScores().currentRelativeScore()).isZero();

        assertThat(courseForDashboard.participationResults().size()).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getScoresAndParticipationResultsForPastCourse() {
        // Create course with assessment due date passed.
        Course pastCourse = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        CourseForDashboardDTO courseForDashboard = courseScoreCalculationService.getScoresAndParticipationResults(pastCourse, null, student.getId());
        assertThat(courseForDashboard.course()).isEqualTo(pastCourse);
        CourseScoresDTO totalCourseScores = courseForDashboard.totalScores();
        assertThat(totalCourseScores.maxPoints()).isEqualTo(5.0);
        assertThat(totalCourseScores.reachablePoints()).isEqualTo(5.0);
        assertThat(totalCourseScores.reachablePresentationPoints()).isZero();
        assertThat(totalCourseScores.studentScores().absoluteScore()).isZero();
        assertThat(totalCourseScores.studentScores().relativeScore()).isZero();
        assertThat(totalCourseScores.studentScores().currentRelativeScore()).isZero();

        CourseScoresDTO programmingExerciseScores = courseForDashboard.programmingScores();
        assertThat(programmingExerciseScores.maxPoints()).isZero();
        assertThat(programmingExerciseScores.reachablePoints()).isZero();
        assertThat(programmingExerciseScores.studentScores().absoluteScore()).isZero();
        assertThat(programmingExerciseScores.studentScores().relativeScore()).isZero();
        assertThat(programmingExerciseScores.studentScores().currentRelativeScore()).isZero();

        CourseScoresDTO quizExerciseScores = courseForDashboard.quizScores();
        assertThat(quizExerciseScores.maxPoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.reachablePoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.studentScores().absoluteScore()).isZero();
        assertThat(quizExerciseScores.studentScores().relativeScore()).isZero();
        assertThat(quizExerciseScores.studentScores().currentRelativeScore()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getScoresAndParticipationResultsForPastCourseWithGradedPresentations() {
        Course pastCourse = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        pastCourse.setPresentationScore(null);

        GradingScale gradingScale = GradingScaleFactory.generateGradingScaleForCourse(pastCourse, 5, 37.5);
        gradingScaleRepository.save(gradingScale);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        pastCourse.getExercises().forEach(exercise -> {
            exercise.getStudentParticipations().forEach(participation -> {
                participation.setPresentationScore(100.0);
                studentParticipationRepository.save(participation);
            });
        });

        CourseForDashboardDTO courseForDashboard = courseScoreCalculationService.getScoresAndParticipationResults(pastCourse, gradingScale, student.getId());
        assertThat(courseForDashboard.course()).isEqualTo(pastCourse);
        CourseScoresDTO totalCourseScores = courseForDashboard.totalScores();
        assertThat(totalCourseScores.maxPoints()).isEqualTo(8.0);
        assertThat(totalCourseScores.reachablePoints()).isEqualTo(8.0);
        assertThat(totalCourseScores.reachablePresentationPoints()).isEqualTo(3.0);
        assertThat(totalCourseScores.studentScores().absoluteScore()).isEqualTo(3.0);
        assertThat(totalCourseScores.studentScores().relativeScore()).isEqualTo(37.5);
        assertThat(totalCourseScores.studentScores().currentRelativeScore()).isEqualTo(37.5);

        CourseScoresDTO programmingExerciseScores = courseForDashboard.programmingScores();
        assertThat(programmingExerciseScores.studentScores().presentationScore()).isZero();

        CourseScoresDTO quizExerciseScores = courseForDashboard.quizScores();
        assertThat(quizExerciseScores.maxPoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.reachablePoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.studentScores().absoluteScore()).isZero();
        assertThat(quizExerciseScores.studentScores().relativeScore()).isZero();
        assertThat(quizExerciseScores.studentScores().currentRelativeScore()).isZero();
    }

    @Test
    void calculateCourseScoreWithNoParticipations() {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        StudentScoresDTO studentScore = courseScoreCalculationService.calculateCourseScoreForStudent(course, null, student.getId(), Collections.emptyList(),
                new MaxAndReachablePoints(100.00, 100.00, 0.0), Collections.emptyList());
        assertThat(studentScore.absoluteScore()).isZero();
        assertThat(studentScore.relativeScore()).isZero();
        assertThat(studentScore.currentRelativeScore()).isZero();
        assertThat(studentScore.presentationScore()).isZero();

        PlagiarismVerdict mostSeverePlagiarismVerdict = courseScoreCalculationService.findMostServerePlagiarismVerdict(Collections.emptyList());
        assertThat(mostSeverePlagiarismVerdict).isNull();
        boolean presentationScorePassed = courseScoreCalculationService.isPresentationScoreSufficientForBonus(studentScore.presentationScore(), course.getPresentationScore());
        assertThat(presentationScorePassed).isFalse();
    }

    @Test
    void getResultsForParticipationEdgeCases() {
        ZonedDateTime dueDate = ZonedDateTime.now().plusSeconds(10);
        course.getExercises().forEach(ex -> ex.setDueDate(dueDate));

        exerciseRepository.saveAll(course.getExercises());

        // Test null participation case.
        assertThat(courseScoreCalculationService.getResultForParticipation(null, dueDate)).isNull();

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        assertThat(studentParticipations).isNotEmpty();

        // Test with multiple results to assert they are sorted.
        StudentParticipation studentParticipation = studentParticipations.getFirst();
        participationUtilService.createSubmissionAndResult(studentParticipation, 50, true);
        participationUtilService.createSubmissionAndResult(studentParticipation, 40, true);
        Result latestResult = participationUtilService.createSubmissionAndResult(studentParticipation, 60, true);

        // Test getting the latest rated result.
        studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());
        assertThat(courseScoreCalculationService.getResultForParticipation(studentParticipations.getFirst(), dueDate).getScore()).isEqualTo(latestResult.getScore());

        // Test with latest rated result after the due date and grace period.
        latestResult.setCompletionDate(dueDate.plusSeconds(30L)); // Due date was set 10 seconds in the future, add more than that.
        resultRepository.save(latestResult);

        studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());
        // Should retrieve the latest result before the due date.
        assertThat(courseScoreCalculationService.getResultForParticipation(studentParticipations.getFirst(), dueDate).getScore()).isEqualTo(40L);
    }
}
