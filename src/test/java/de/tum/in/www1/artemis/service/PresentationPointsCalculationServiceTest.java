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

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        course = database.addEmptyCourse();
    }

    @Test
    void calculateReachablePresentationPointsWithoutBaseReachablePoints() {
        // GIVEN
        GradingScale gradingScale = new GradingScale();
        gradingScale.setPresentationsNumber(2);
        gradingScale.setPresentationsWeight(20.0);
        gradingScale.setCourse(course);
        gradingScaleRepository.save(gradingScale);

        // WHEN
        var reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, 0.0);

        // THEN
        assertThat(reachablePresentationPoints).isZero();
    }

    @Test
    void calculateReachablePresentationPointsWithoutGradingScale() {
        // WHEN
        var reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(null, 80.0);

        // THEN
        assertThat(reachablePresentationPoints).isZero();
    }

    @Test
    void calculateReachablePresentationPoints() {
        // GIVEN
        GradingScale gradingScale = new GradingScale();
        gradingScale.setPresentationsNumber(2);
        gradingScale.setPresentationsWeight(20.0);
        gradingScale.setCourse(course);
        gradingScaleRepository.save(gradingScale);

        // WHEN
        var reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, 80.0);

        // THEN
        assertThat(reachablePresentationPoints).isEqualTo(20.0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void calculatePresentationPoints() {
        // GIVEN
        GradingScale gradingScale = new GradingScale();
        gradingScale.setPresentationsNumber(1);
        gradingScale.setPresentationsWeight(20.0);
        gradingScale.setCourse(course);
        gradingScaleRepository.save(gradingScale);

        ProgrammingExercise exercise = database.addProgrammingExerciseToCourse(course, false);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(80.0);
        exerciseRepository.save(exercise);

        User student = database.getUserByLogin(TEST_PREFIX + "student1");
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, student.getLogin());
        participation.setPresentationScore(50.0);
        studentParticipationRepository.save(participation);

        // WHEN
        var presentationPoints = presentationPointsCalculationService.calculatePresentationPointsForStudentId(gradingScale, student.getId(), 20);

        // THEN
        assertThat(presentationPoints).isEqualTo(10.0);
    }
}
