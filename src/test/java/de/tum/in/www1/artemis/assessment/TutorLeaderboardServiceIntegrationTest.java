package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.TutorLeaderboardService;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;

class TutorLeaderboardServiceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "tlbsitest"; // only lower case is supported

    @Autowired
    private TutorLeaderboardService tutorLeaderboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    private static final int TUTOR_COUNT = 1;

    private static final int ASSESSMENT_COUNT = 2;

    private Course course;

    private Exercise exercise;

    /**
     * Prepares the testing suite by initializing variables and mocks
     */
    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 1, TUTOR_COUNT, 0, 1);
        // Tutors should only be part of "leaderboardgroup"
        for (int i = 1; i <= TUTOR_COUNT; i++) {
            var tutor = database.getUserByLogin(TEST_PREFIX + "tutor" + i);
            tutor.setGroups(Set.of("leaderboardgroup"));
            userRepository.save(tutor);
        }
        var student1 = database.getUserByLogin(TEST_PREFIX + "student1");
        var tutor1 = database.getUserByLogin(TEST_PREFIX + "tutor1");

        course = database.addCourseWithOneModelingExercise();
        course.setTeachingAssistantGroupName("leaderboardgroup");
        courseRepository.save(course);

        exercise = course.getExercises().iterator().next();

        var modelingSubmission = database.addModelingSubmissionWithEmptyResult((ModelingExercise) exercise, "", student1.getLogin());
        var result = database.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, tutor1, 40.0, true).getLatestResult();
        database.addRatingToResult(result, 2);

        modelingSubmission = database.addModelingSubmissionWithEmptyResult((ModelingExercise) exercise, "", student1.getLogin());
        result = database.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, tutor1, 60.0, true).getLatestResult();
        database.addRatingToResult(result, 5);
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
