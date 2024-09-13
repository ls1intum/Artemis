package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.service.PresentationPointsCalculationService;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleFactory;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PresentationPointsCalculationServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "ppcservicetest";

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private PresentationPointsCalculationService presentationPointsCalculationService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course;

    private StudentParticipation studentParticipation;

    @BeforeEach
    void init() {
        course = courseUtilService.addEmptyCourse();

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);

        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(80.0);
        exerciseRepository.save(exercise);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, student.getLogin());
        studentParticipationRepository.save(studentParticipation);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void calculateReachableAndAchievedPresentationPointsWithoutBaseReachablePoints() {
        // GIVEN
        GradingScale gradingScale = GradingScaleFactory.generateGradingScaleForCourse(course, 2, 20.0);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // WHEN
        double reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, 0.0);
        double presentationPoints = presentationPointsCalculationService.calculatePresentationPointsForStudentId(gradingScale, student.getId(), 0.0);

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
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // WHEN
        double reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(null, 80.0);
        double presentationPoints = presentationPointsCalculationService.calculatePresentationPointsForStudentId(null, student.getId(), 20);

        // THEN
        assertThat(reachablePresentationPoints).isZero();
        assertThat(presentationPoints).isZero();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void calculateReachableAndAchievedPresentationPoints() {
        // GIVEN
        GradingScale gradingScale = GradingScaleFactory.generateGradingScaleForCourse(course, 1, 20.0);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        studentParticipation.setPresentationScore(50.0);
        studentParticipationRepository.save(studentParticipation);

        // WHEN
        double reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, 80.0);
        double presentationPoints = presentationPointsCalculationService.calculatePresentationPointsForStudentId(gradingScale, student.getId(), 20);

        // THEN
        assertThat(reachablePresentationPoints).isEqualTo(20.0);
        assertThat(presentationPoints).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void calculateAchievedPresentationPointsWithoutPresentations() {
        // GIVEN
        GradingScale gradingScale = GradingScaleFactory.generateGradingScaleForCourse(course, 1, 20.0);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // WHEN
        double reachablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, 80.0);
        double presentationPoints = presentationPointsCalculationService.calculatePresentationPointsForStudentId(gradingScale, student.getId(), 20);

        // THEN
        assertThat(reachablePresentationPoints).isEqualTo(20.0);
        assertThat(presentationPoints).isEqualTo(0.0);
    }
}
