package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.service.TutorLeaderboardService;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;

class TutorLeaderboardServiceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TutorLeaderboardService tutorLeaderboardService;

    final private static int TUTOR_COUNT = 1;

    final private static int ASSESSMENT_COUNT = 2;

    private Course course;

    private Exercise exercise;

    /**
     * Prepares the testing suite by initializing variables and mocks
     */
    @BeforeEach
    void initTestCase() {
        var users = database.addUsers(10, TUTOR_COUNT, 0, 2);
        var student1 = users.get(0);
        var tutor1 = database.getUserByLogin("tutor1");

        course = database.addCourseWithOneModelingExercise();
        exercise = course.getExercises().iterator().next();

        var modelingSubmission = database.addModelingSubmissionWithEmptyResult((ModelingExercise) exercise, "", student1.getLogin());
        var result = database.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, tutor1, 40.0, true).getLatestResult();
        database.addRatingToResult(result, 2);

        modelingSubmission = database.addModelingSubmissionWithEmptyResult((ModelingExercise) exercise, "", student1.getLogin());
        result = database.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, tutor1, 60.0, true).getLatestResult();
        database.addRatingToResult(result, 5);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    private void assertLeaderboardData(List<TutorLeaderboardDTO> leaderboardData) {
        assertThat(leaderboardData).hasSize(TUTOR_COUNT);
        assertThat(leaderboardData.get(0).getNumberOfAssessments()).isEqualTo(2);
        assertThat(leaderboardData.get(0).getNumberOfAcceptedComplaints()).isZero();
        assertThat(leaderboardData.get(0).getNumberOfTutorComplaints()).isZero();
        assertThat(leaderboardData.get(0).getNumberOfNotAnsweredMoreFeedbackRequests()).isZero();
        assertThat(leaderboardData.get(0).getNumberOfComplaintResponses()).isZero();
        assertThat(leaderboardData.get(0).getNumberOfAnsweredMoreFeedbackRequests()).isZero();
        assertThat(leaderboardData.get(0).getNumberOfTutorMoreFeedbackRequests()).isZero();
        assertThat(leaderboardData.get(0).getPoints()).isEqualTo(exercise.getMaxPoints() * ASSESSMENT_COUNT);
        assertThat(leaderboardData.get(0).getAverageScore()).isEqualTo(50);
        assertThat(leaderboardData.get(0).getAverageRating()).isEqualTo(3.5);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testLeaderboardData_forCourseWithExercises() {
        Long[] exerciseIds = { exercise.getId() };
        var leaderboardData = tutorLeaderboardService.getCourseLeaderboard(course, new HashSet<>(Arrays.asList(exerciseIds)));
        assertLeaderboardData(leaderboardData);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testLeaderboardData_forExercise() {
        var leaderboardData = tutorLeaderboardService.getExerciseLeaderboard(exercise);
        assertLeaderboardData(leaderboardData);
    }
}
