package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

class PresentationPointsCalculationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "ppcservicetest";

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private PresentationPointsCalculationService presentationPointsCalculationService;

    private Course course;

    private GradingScale gradingScale;

    private StudentParticipation studentParticipation;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        course = database.addEmptyCourse();

        gradingScale = new GradingScale();
        gradingScale.setCourse(course);
        gradingScaleRepository.save(gradingScale);

        ProgrammingExercise exercise = database.addProgrammingExerciseToCourse(course, false);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(80.0);
        exerciseRepository.save(exercise);

        User student = database.getUserByLogin(TEST_PREFIX + "student1");

        studentParticipation = database.addStudentParticipationForProgrammingExercise(exercise, student.getLogin());
        studentParticipationRepository.save(studentParticipation);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void calculateReachableAndAchievedPresentationPointsWithoutBaseReachablePoints() {
        // GIVEN
        gradingScale.setPresentationsNumber(2);
        gradingScale.setPresentationsWeight(20.0);
        gradingScaleRepository.save(gradingScale);
        User student = database.getUserByLogin(TEST_PREFIX + "student1");

        // WHEN
        var reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, 0.0);
        var presentationPoints = presentationPointsCalculationService.calculatePresentationPointsForStudentId(gradingScale, student.getId(), 0.0);

        // THEN
        assertThat(reachablePresentationPoints).isZero();
        assertThat(presentationPoints).isZero();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void calculateReachableAndAchievedPresentationPointsWithoutGradingScale() {
        // GIVEN
        studentParticipation.setPresentationScore(50.0);
        studentParticipationRepository.save(studentParticipation);
        User student = database.getUserByLogin(TEST_PREFIX + "student1");

        // WHEN
        var reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(null, 80.0);
        var presentationPoints = presentationPointsCalculationService.calculatePresentationPointsForStudentId(null, student.getId(), 20);

        // THEN
        assertThat(reachablePresentationPoints).isZero();
        assertThat(presentationPoints).isZero();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void calculateReachableAndAchievedPresentationPoints() {
        // GIVEN
        gradingScale.setPresentationsNumber(1);
        gradingScale.setPresentationsWeight(20.0);
        gradingScaleRepository.save(gradingScale);

        User student = database.getUserByLogin(TEST_PREFIX + "student1");
        studentParticipation.setPresentationScore(50.0);
        studentParticipationRepository.save(studentParticipation);

        // WHEN
        var reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, 80.0);
        var presentationPoints = presentationPointsCalculationService.calculatePresentationPointsForStudentId(gradingScale, student.getId(), 20);

        // THEN
        assertThat(reachablePresentationPoints).isEqualTo(20.0);
        assertThat(presentationPoints).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void calculateAchievedPresentationPointsWithoutPresentations() {
        // GIVEN
        gradingScale.setPresentationsNumber(1);
        gradingScale.setPresentationsWeight(20.0);
        gradingScaleRepository.save(gradingScale);

        User student = database.getUserByLogin(TEST_PREFIX + "student1");

        // WHEN
        var reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, 80.0);
        var presentationPoints = presentationPointsCalculationService.calculatePresentationPointsForStudentId(gradingScale, student.getId(), 20);

        // THEN
        assertThat(reachablePresentationPoints).isEqualTo(20.0);
        assertThat(presentationPoints).isEqualTo(0.0);
    }
}
