package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
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
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismCaseService;

class CourseScoreCalculationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private CourseScoreCalculationService courseScoreCalculationService;

    @Autowired
    private UserRepository userRepository;

    private Course course;

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @BeforeEach
    void init() {
        database.addUsers(2, 2, 0, 1);
        course = database.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(false);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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

        User student = userRepository.findOneByLogin("student1").get();

        var courseResult = courseScoreCalculationService.calculateCourseScores(course.getId(), List.of(student.getId()));
        assertThat(courseResult.maxPoints()).isEqualTo(0.0);
        assertThat(courseResult.reachablePoints()).isEqualTo(0.0);
        assertThat(courseResult.studentScores()).hasSize(1);
        assertThat(courseResult.studentScores().get(0).absolutePoints()).isEqualTo(0.0);
        assertThat(courseResult.studentScores().get(0).relativeScore()).isEqualTo(0.0);
        assertThat(courseResult.studentScores().get(0).currentRelativeScore()).isEqualTo(0.0);
        assertThat(courseResult.studentScores().get(0).getAbsolutePointsEligibleForBonus()).isEqualTo(0.0);

    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoreForStudentWithMultipleResultsInParticipation(boolean withDueDate) {

        ZonedDateTime dueDate = withDueDate ? ZonedDateTime.now() : null;
        course.getExercises().forEach(ex -> ex.setDueDate(dueDate));

        exerciseRepository.saveAll(course.getExercises());

        User student = userRepository.findOneByLogin("student1").get();

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

}
