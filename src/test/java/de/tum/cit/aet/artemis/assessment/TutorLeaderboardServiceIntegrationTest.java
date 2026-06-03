package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.service.TutorLeaderboardService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.TutorLeaderboardDTO;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TutorLeaderboardServiceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tlbsitest"; // only lower case is supported

    @Autowired
    private TutorLeaderboardService tutorLeaderboardService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private UserCourseRoleRepository userCourseRoleRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private static final int TUTOR_COUNT = 1;

    private static final int ASSESSMENT_COUNT = 2;

    private Course course;

    private Exercise exercise;

    /**
     * Prepares the testing suite by initializing variables and mocks
     */
    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, TUTOR_COUNT, 0, 1);
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var tutor1 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var instructor1 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        userUtilService.enrollUserInCourse(tutor1, course, CourseRole.TEACHING_ASSISTANT);
        userUtilService.enrollUserInCourse(instructor1, course, CourseRole.INSTRUCTOR);
        userUtilService.enrollUserInCourse(student1, course, CourseRole.STUDENT);

        exercise = course.getExercises().iterator().next();

        var modelingSubmission = modelingExerciseUtilService.addModelingSubmissionWithEmptyResult((ModelingExercise) exercise, "", student1.getLogin());
        var result = participationUtilService.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, tutor1, 40.0, true).getLatestResult();
        participationUtilService.addRatingToResult(result, 2);

        modelingSubmission = modelingExerciseUtilService.addModelingSubmissionWithEmptyResult((ModelingExercise) exercise, "", student1.getLogin());
        result = participationUtilService.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, tutor1, 60.0, true).getLatestResult();
        participationUtilService.addRatingToResult(result, 5);
    }

    private void assertLeaderboardData(List<TutorLeaderboardDTO> leaderboardData) {
        assertThat(leaderboardData).hasSize(TUTOR_COUNT);
        assertThat(leaderboardData.getFirst().numberOfAssessments()).isEqualTo(2);
        assertThat(leaderboardData.getFirst().numberOfAcceptedComplaints()).isZero();
        assertThat(leaderboardData.getFirst().numberOfTutorComplaints()).isZero();
        assertThat(leaderboardData.getFirst().numberOfNotAnsweredMoreFeedbackRequests()).isZero();
        assertThat(leaderboardData.getFirst().numberOfComplaintResponses()).isZero();
        assertThat(leaderboardData.getFirst().numberOfAnsweredMoreFeedbackRequests()).isZero();
        assertThat(leaderboardData.getFirst().numberOfTutorMoreFeedbackRequests()).isZero();
        assertThat(leaderboardData.getFirst().points()).isEqualTo(exercise.getMaxPoints() * ASSESSMENT_COUNT);
        assertThat(leaderboardData.getFirst().averageScore()).isEqualTo(50);
        assertThat(leaderboardData.getFirst().averageRating()).isEqualTo(3.5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLeaderboardData_forCourseWithExercises() {
        Long[] exerciseIds = { exercise.getId() };
        var leaderboardData = tutorLeaderboardService.getCourseLeaderboard(course, new HashSet<>(Arrays.asList(exerciseIds)));
        assertLeaderboardData(leaderboardData);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLeaderboardData_forExercise() {
        var leaderboardData = tutorLeaderboardService.getExerciseLeaderboard(exercise);
        assertLeaderboardData(leaderboardData);
    }
}
