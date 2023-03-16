package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.CourseForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.CourseScoresDTO;
import de.tum.in.www1.artemis.web.rest.dto.CourseScoresForExamBonusSourceDTO;
import de.tum.in.www1.artemis.web.rest.dto.StudentScoresDTO;

class CourseScoreCalculationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "cscservicetest";

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private CourseScoreCalculationService courseScoreCalculationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResultRepository resultRepository;

    private Course course;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 2, 2, 0, 1);
        course = database.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoresForExamBonusSourceWithNotIncludedExercises() {
        var exerciseList = new ArrayList<>(course.getExercises());
        exerciseList.sort(Comparator.comparing(Exercise::getId));

        var exercise = exerciseList.get(0);
        exercise.setDueDate(null);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);

        // Other exercises should have due dates in the future which is also not included.
        ZonedDateTime now = ZonedDateTime.now();
        exerciseList.stream().skip(1).forEach(ex -> assertThat(ex.getDueDate()).isAfter(now));

        exerciseRepository.save(exercise);

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        CourseScoresForExamBonusSourceDTO courseResult = courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course.getId(), List.of(student.getId()));
        assertThat(courseResult.maxPoints()).isEqualTo(0.0);
        assertThat(courseResult.reachablePoints()).isEqualTo(0.0);
        assertThat(courseResult.studentScores()).hasSize(1);
        assertThat(courseResult.studentScores().get(0).getAbsoluteScore()).isEqualTo(0.0);
        assertThat(courseResult.studentScores().get(0).getRelativeScore()).isEqualTo(0.0);
        assertThat(courseResult.studentScores().get(0).getCurrentRelativeScore()).isEqualTo(0.0);
        assertThat(courseResult.studentScores().get(0).getAbsolutePointsEligibleForBonus()).isEqualTo(0.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoreForExamBonusSourceWithoutExercises() {
        Course course = database.addEmptyCourse();

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        var courseResult = courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course.getId(), List.of(student.getId()));
        assertThat(courseResult).isNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoreForExamBonusSourceWithMultipleResultsInParticipation(boolean withDueDate) {

        ZonedDateTime dueDate = withDueDate ? ZonedDateTime.now() : null;
        course.getExercises().forEach(ex -> ex.setDueDate(dueDate));

        exerciseRepository.saveAll(course.getExercises());

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        List<StudentParticipation> studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        assertThat(studentParticipations).isNotEmpty();

        // Test with multiple results to assert they are sorted.
        StudentParticipation studentParticipation = studentParticipations.get(0);
        database.createSubmissionAndResult(studentParticipation, 50, true);
        database.createSubmissionAndResult(studentParticipation, 40, true);
        database.createSubmissionAndResult(studentParticipation, 60, true);

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
        assertThat(result.getScore()).isEqualTo(0.0);
        result.score(null);

        CourseScoresForExamBonusSourceDTO courseResult = courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course.getId(), List.of(student.getId()));
        assertThat(courseResult.studentScores()).hasSize(1);
        assertThat(courseResult.studentScores().get(0).getStudentId()).isEqualTo(student.getId());
        assertThat(courseResult.studentScores().get(0).getRelativeScore()).isEqualTo(16.0);
        assertThat(courseResult.studentScores().get(0).getAbsoluteScore()).isEqualTo(4.0);
        assertThat(courseResult.studentScores().get(0).getCurrentRelativeScore()).isEqualTo(80.0);
        assertThat(courseResult.studentScores().get(0).getPresentationScore()).isEqualTo(0);
        assertThat(courseResult.studentScores().get(0).isPresentationScorePassed()).isFalse();
        assertThat(courseResult.studentScores().get(0).getMostSeverePlagiarismVerdict()).isNull();
        assertThat(courseResult.studentScores().get(0).getAbsolutePointsEligibleForBonus()).isEqualTo(0.0);
    }

    @Test
    @WithMockUser()
    void getScoresAndParticipationResultsWithNotIncludedExercise() {
        var exerciseList = new ArrayList<>(course.getExercises());
        exerciseList.sort(Comparator.comparing(Exercise::getId));

        var exercise = exerciseList.get(0);
        exercise.setDueDate(null);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);

        exerciseRepository.save(exercise);

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        CourseForDashboardDTO courseForDashboard = courseScoreCalculationService.getScoresAndParticipationResults(course, student.getId());
        assertThat(courseForDashboard.course()).isEqualTo(course);
        CourseScoresDTO totalCourseScores = courseForDashboard.scoresPerExerciseType().get("total");
        assertThat(totalCourseScores.maxPoints()).isEqualTo(0.0);
        assertThat(totalCourseScores.reachablePoints()).isEqualTo(0.0);
        assertThat(totalCourseScores.studentScores().getAbsoluteScore()).isEqualTo(0.0);
        assertThat(totalCourseScores.studentScores().getRelativeScore()).isEqualTo(0.0);
        assertThat(totalCourseScores.studentScores().getCurrentRelativeScore()).isEqualTo(0.0);

        assertThat(courseForDashboard.participationResults().size()).isEqualTo(5);
    }

    @Test
    @WithMockUser()
    void getScoresAndParticipationResultsForPastCourse() {
        // Create course with assessment due date passed.
        Course pastCourse = database.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        CourseForDashboardDTO courseForDashboard = courseScoreCalculationService.getScoresAndParticipationResults(pastCourse, student.getId());
        assertThat(courseForDashboard.course()).isEqualTo(pastCourse);
        CourseScoresDTO totalCourseScores = courseForDashboard.scoresPerExerciseType().get("total");
        assertThat(totalCourseScores.maxPoints()).isEqualTo(5.0);
        assertThat(totalCourseScores.reachablePoints()).isEqualTo(5.0);
        assertThat(totalCourseScores.studentScores().getAbsoluteScore()).isEqualTo(0.0);
        assertThat(totalCourseScores.studentScores().getRelativeScore()).isEqualTo(0.0);
        assertThat(totalCourseScores.studentScores().getCurrentRelativeScore()).isEqualTo(0.0);

        CourseScoresDTO programmingExerciseScores = courseForDashboard.scoresPerExerciseType().get(ExerciseType.PROGRAMMING.getExerciseTypeAsString());
        assertThat(programmingExerciseScores.maxPoints()).isEqualTo(0.0);
        assertThat(programmingExerciseScores.reachablePoints()).isEqualTo(0.0);
        assertThat(programmingExerciseScores.studentScores().getAbsoluteScore()).isEqualTo(0.0);
        assertThat(programmingExerciseScores.studentScores().getRelativeScore()).isEqualTo(0.0);
        assertThat(programmingExerciseScores.studentScores().getCurrentRelativeScore()).isEqualTo(0.0);

        CourseScoresDTO quizExerciseScores = courseForDashboard.scoresPerExerciseType().get(ExerciseType.QUIZ.getExerciseTypeAsString());
        assertThat(quizExerciseScores.maxPoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.reachablePoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.studentScores().getAbsoluteScore()).isEqualTo(0.0);
        assertThat(quizExerciseScores.studentScores().getRelativeScore()).isEqualTo(0.0);
        assertThat(quizExerciseScores.studentScores().getCurrentRelativeScore()).isEqualTo(0.0);
    }

    @Test
    void calculateCourseScoreWithNoParticipations() {

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        StudentScoresDTO studentScore = courseScoreCalculationService.calculateCourseScoreForStudent(course, student.getId(), Collections.emptyList(),
                new CourseScoreCalculationService.MaxAndReachablePoints(100.00, 100.00), Collections.emptyList());
        assertThat(studentScore.getAbsoluteScore()).isEqualTo(0.0);
        assertThat(studentScore.getRelativeScore()).isEqualTo(0.0);
        assertThat(studentScore.getCurrentRelativeScore()).isEqualTo(0.0);
        assertThat(studentScore.getPresentationScore()).isEqualTo(0);

        PlagiarismVerdict mostSeverePlagiarismVerdict = courseScoreCalculationService.findMostServerePlagiarismVerdict(Collections.emptyList());
        assertThat(mostSeverePlagiarismVerdict).isNull();
        boolean presentationScorePassed = courseScoreCalculationService.isPresentationScoreSufficientForBonus(studentScore.getPresentationScore(), course.getPresentationScore());
        assertThat(presentationScorePassed).isFalse();

    }

    @Test
    void getResultsForParticipationEdgeCases() {

        ZonedDateTime dueDate = ZonedDateTime.now();
        course.getExercises().forEach(ex -> ex.setDueDate(dueDate));

        exerciseRepository.saveAll(course.getExercises());

        // Test null participation case.
        assertThat(courseScoreCalculationService.getResultForParticipation(null, dueDate)).isNull();

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        var studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        assertThat(studentParticipations).isNotEmpty();

        // Test with multiple results to assert they are sorted.
        StudentParticipation studentParticipation = studentParticipations.get(0);
        database.createSubmissionAndResult(studentParticipation, 50, true);
        database.createSubmissionAndResult(studentParticipation, 40, true);
        Result latestResult = database.createSubmissionAndResult(studentParticipation, 60, true);

        // Test getting the latest rated result.
        studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());
        assertThat(courseScoreCalculationService.getResultForParticipation(studentParticipations.get(0), dueDate).getScore()).isEqualTo(latestResult.getScore());

        // Test with latest rated result after the due date and grade period.
        latestResult.setCompletionDate(dueDate.plusSeconds(30L)); // Grade Period is 10 seconds, add more than that.
        resultRepository.save(latestResult);

        studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());
        assertThat(courseScoreCalculationService.getResultForParticipation(studentParticipations.get(0), dueDate).getScore()).isEqualTo(0L);
    }
}
