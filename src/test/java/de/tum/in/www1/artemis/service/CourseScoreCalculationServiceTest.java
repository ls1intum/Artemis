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
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismCaseService;

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
    void calculateCourseScoreWithNotIncludedExercises() {
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

        var courseResult = courseScoreCalculationService.calculateCourseScores(course.getId(), List.of(student.getId()));
        assertThat(courseResult.maxPoints()).isEqualTo(0.0);
        assertThat(courseResult.reachablePoints()).isEqualTo(0.0);
        assertThat(courseResult.studentScores()).hasSize(1);
        assertThat(courseResult.studentScores().get(0).absolutePoints()).isEqualTo(0.0);
        assertThat(courseResult.studentScores().get(0).relativeScore()).isEqualTo(0.0);
        assertThat(courseResult.studentScores().get(0).currentRelativeScore()).isEqualTo(0.0);
        assertThat(courseResult.studentScores().get(0).getAbsolutePointsEligibleForBonus()).isEqualTo(0.0);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoreWithoutExercises() {
        Course course = database.addEmptyCourse();

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        var courseResult = courseScoreCalculationService.calculateCourseScores(course.getId(), List.of(student.getId()));
        assertThat(courseResult).isNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoreForStudentWithMultipleResultsInParticipation(boolean withDueDate) {

        ZonedDateTime dueDate = withDueDate ? ZonedDateTime.now() : null;
        course.getExercises().forEach(ex -> ex.setDueDate(dueDate));

        exerciseRepository.saveAll(course.getExercises());

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        var studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        assertThat(studentParticipations).isNotEmpty();

        // Test with multiple results to assert they are sorted.
        StudentParticipation studentParticipation = studentParticipations.get(0);
        database.createSubmissionAndResult(studentParticipation, 50, true);
        database.createSubmissionAndResult(studentParticipation, 40, true);
        database.createSubmissionAndResult(studentParticipation, 60, true);

        studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        // Test with null result set.
        studentParticipations.get(1).setResults(null);

        // Test with empty result set.
        studentParticipations.get(2).setResults(Collections.emptySet());

        // Test with null score in result.

        // QuizExercise is selected because it has already a score of 0 in the initial test data and we have one participation for each exercise type.
        // Besides that, exercise type is irrelevant for this test.
        StudentParticipation studentParticipationWithZeroScore = studentParticipations.stream().filter(participation -> participation.getExercise() instanceof QuizExercise)
                .findFirst().orElseThrow();
        Result result = studentParticipationWithZeroScore.getResults().iterator().next();
        assertThat(result.getScore()).isEqualTo(0.0);
        result.score(null);

        var studentScoreResult = courseScoreCalculationService.calculateCourseScoreForStudent(student.getId(), studentParticipations, 25.0, 5.0,
                new PlagiarismCaseService.PlagiarismMapping(Collections.emptyMap()));
        assertThat(studentScoreResult.studentId()).isEqualTo(student.getId());
        assertThat(studentScoreResult.relativeScore()).isEqualTo(16.0);
        assertThat(studentScoreResult.absolutePoints()).isEqualTo(4.0);
        assertThat(studentScoreResult.currentRelativeScore()).isEqualTo(80.0);
        assertThat(studentScoreResult.achievedPresentationScore()).isEqualTo(0);
        assertThat(studentScoreResult.presentationScorePassed()).isFalse();
        assertThat(studentScoreResult.mostSeverePlagiarismVerdict()).isNull();
        assertThat(studentScoreResult.getAbsolutePointsEligibleForBonus()).isEqualTo(0.0);

    }

    @Test
    void calculateCourseScoreWithNoParticipations() {

        User student = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        var studentScore = courseScoreCalculationService.calculateCourseScoreForStudent(student.getId(), Collections.emptyList(), 100, 100,
                new PlagiarismCaseService.PlagiarismMapping(Collections.emptyMap()));
        assertThat(studentScore.studentId()).isEqualTo(student.getId());
        assertThat(studentScore.absolutePoints()).isEqualTo(0.0);
        assertThat(studentScore.relativeScore()).isEqualTo(0.0);
        assertThat(studentScore.currentRelativeScore()).isEqualTo(0.0);
        assertThat(studentScore.achievedPresentationScore()).isEqualTo(0);
        assertThat(studentScore.mostSeverePlagiarismVerdict()).isNull();
        assertThat(studentScore.getAbsolutePointsEligibleForBonus()).isEqualTo(0.0);
        assertThat(studentScore.hasParticipated()).isFalse();
        assertThat(studentScore.presentationScorePassed()).isFalse();

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
