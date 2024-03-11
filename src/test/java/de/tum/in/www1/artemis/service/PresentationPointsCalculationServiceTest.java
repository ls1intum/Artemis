package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.assessment.GradingScaleFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

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
