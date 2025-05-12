package de.tum.cit.aet.artemis.atlas.profile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Set;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.CourseLearnerProfileDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

class LearnerProfileIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "learnerprofiledatabase";

    private static final int NUMBER_OF_STUDENTS = 1;

    private static final String STUDENT1_OF_COURSE = TEST_PREFIX + "student1";

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");

        Course course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);

        // Create learner profiles with valid feedback values
        Set<User> users = userTestRepository.findAllByUserPrefix(TEST_PREFIX);
        for (User user : users) {
            LearnerProfile learnerProfile = new LearnerProfile();
            learnerProfile.setUser(user);
            learnerProfile.setFeedbackAlternativeStandard(1);
            learnerProfile.setFeedbackFollowupSummary(1);
            learnerProfile.setFeedbackBriefDetailed(1);
            user.setLearnerProfile(learnerProfile);

            CourseLearnerProfile courseLearnerProfile = new CourseLearnerProfile();
            courseLearnerProfile.setLearnerProfile(learnerProfile);
            courseLearnerProfile.setCourse(course);
            courseLearnerProfile.setAimForGradeOrBonus(1);
            courseLearnerProfile.setRepetitionIntensity(1);
            courseLearnerProfile.setTimeInvestment(1);
            learnerProfile.addCourseLearnerProfile(courseLearnerProfile);
        }
        userTestRepository.saveAll(users);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldReturnCourseProfilesForUser() throws Exception {

        Set<CourseLearnerProfileDTO> response = request.getSet("/api/atlas/course-learner-profiles", HttpStatus.OK, CourseLearnerProfileDTO.class);

        Set<CourseLearnerProfile> profiles = courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE);

        for (CourseLearnerProfile profile : profiles) {
            assertThat(response.iterator().next()).isEqualTo(CourseLearnerProfileDTO.of(profile));
        }
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldRejectInvalidProfileId() throws Exception {

        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(2, 0, "title1", 1, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 1, dto, HttpStatus.BAD_REQUEST);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        request.put("/api/atlas/course-learner-profiles/" + 2, dto, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldNotUpdateWithInvalidValues() throws Exception {

        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(0, 0, "title1", 0, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 6, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 0, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 6, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 0);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 6);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldUpdateLearnerProfile() throws Exception {

        CourseLearnerProfile courseLearnerProfile = courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE).stream().findAny().get();
        var course = courseLearnerProfile.getCourse();
        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(courseLearnerProfile.getId(), course.getId(), course.getTitle(),
                (courseLearnerProfile.getAimForGradeOrBonus()) % 4 + 1, (courseLearnerProfile.getTimeInvestment()) % 4 + 1,
                (courseLearnerProfile.getRepetitionIntensity()) % 4 + 1);

        CourseLearnerProfileDTO response = request.putWithResponseBody("/api/atlas/course-learner-profiles/" + courseLearnerProfile.getId(), dto, CourseLearnerProfileDTO.class,
                HttpStatus.OK);

        assertThat(response).isEqualTo(dto);
        CourseLearnerProfileDTO dbState = CourseLearnerProfileDTO.of(courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE).stream().findAny().get());
        assertThat(dbState).isEqualTo(dto);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldFetchLearnerProfileLazily() {
        User user = userTestRepository.getUserWithGroupsAndAuthorities(STUDENT1_OF_COURSE);
        assertThat(Hibernate.isInitialized(user.getLearnerProfile())).isFalse();
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldNotUpdateFeedbackProfileWithInvalidValues() throws Exception {
        // Test values less than 1
        request.put("/api/atlas/learner-profiles/1", Map.of("feedbackAlternativeStandard", 0, "feedbackFollowupSummary", 1, "feedbackBriefDetailed", 1), HttpStatus.BAD_REQUEST);

        request.put("/api/atlas/learner-profiles/1", Map.of("feedbackAlternativeStandard", 1, "feedbackFollowupSummary", 0, "feedbackBriefDetailed", 1), HttpStatus.BAD_REQUEST);

        request.put("/api/atlas/learner-profiles/1", Map.of("feedbackAlternativeStandard", 1, "feedbackFollowupSummary", 1, "feedbackBriefDetailed", 0), HttpStatus.BAD_REQUEST);

        // Test values greater than 5
        request.put("/api/atlas/learner-profiles/1", Map.of("feedbackAlternativeStandard", 6, "feedbackFollowupSummary", 1, "feedbackBriefDetailed", 1), HttpStatus.BAD_REQUEST);

        request.put("/api/atlas/learner-profiles/1", Map.of("feedbackAlternativeStandard", 1, "feedbackFollowupSummary", 6, "feedbackBriefDetailed", 1), HttpStatus.BAD_REQUEST);

        request.put("/api/atlas/learner-profiles/1", Map.of("feedbackAlternativeStandard", 1, "feedbackFollowupSummary", 1, "feedbackBriefDetailed", 6), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldUpdateFeedbackLearnerProfile() throws Exception {
        // Get current profile
        Map<String, Object> currentProfile = request.get("/api/atlas/learner-profiles", HttpStatus.OK, Map.class);

        // Create new values within valid range (1-5)
        Map<String, Object> updatedProfile = new HashMap<>();
        updatedProfile.put("id", currentProfile.get("id"));
        updatedProfile.put("feedbackAlternativeStandard", 3);
        updatedProfile.put("feedbackFollowupSummary", 4);
        updatedProfile.put("feedbackBriefDetailed", 5);

        // Update profile
        Map<String, Object> response = request.putWithResponseBody("/api/atlas/learner-profiles/" + currentProfile.get("id"), updatedProfile, Map.class, HttpStatus.OK);

        // Verify response matches what we sent
        assertThat(response).isEqualTo(updatedProfile);

        // Verify the update was persisted by fetching again
        Map<String, Object> persistedProfile = request.get("/api/atlas/learner-profiles", HttpStatus.OK, Map.class);
        assertThat(persistedProfile).isEqualTo(updatedProfile);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldRejectInvalidFeedbackProfileId() throws Exception {
        Map<String, Object> profile = Map.of("feedbackAlternativeStandard", 3, "feedbackFollowupSummary", 4, "feedbackBriefDetailed", 5);

        // Test with non-existent ID
        request.put("/api/atlas/learner-profiles/999", profile, HttpStatus.BAD_REQUEST);

        // Test with invalid ID format
        request.put("/api/atlas/learner-profiles/-1", profile, HttpStatus.BAD_REQUEST);
    }
}
