package de.tum.cit.aet.artemis.atlas.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.LearnerProfileDTO;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;

class LearnerProfileResourceTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "learnerprofileresource";

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private LearnerProfileRepository learnerProfileRepository;

    @Autowired
    private UserUtilService userUtilService;

    private User testUser;

    private LearnerProfile testProfile;

    @BeforeEach
    void setup() {
        // Create and save the user
        testUser = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");

        // Create the profile, set the user, and set the profile on the user
        testProfile = new LearnerProfile();
        testProfile.setUser(testUser);
        testProfile.setFeedbackDetail(1);
        testProfile.setFeedbackFormality(1);
        testProfile.setHasSetupFeedbackPreferences(true);
        testUser.setLearnerProfile(testProfile);

        // Save the user (should cascade to profile if mapping is correct)
        userTestRepository.save(testUser);

        // Reload to ensure IDs are set
        testUser = userTestRepository.findById(testUser.getId()).orElseThrow();
        testProfile = learnerProfileRepository.findByUserElseThrow(testUser);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLearnerProfile_Success() throws Exception {
        LearnerProfileDTO response = request.get("/api/atlas/learner-profile", HttpStatus.OK, LearnerProfileDTO.class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testProfile.getId());
        assertThat(response.feedbackDetail()).isEqualTo(1);
        assertThat(response.feedbackFormality()).isEqualTo(1);
        assertThat(response.hasSetupFeedbackPreferences()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLearnerProfile_ProfileNotFound_CreatesNewProfile() throws Exception {
        // Delete the profile to simulate a user without a profile
        learnerProfileRepository.delete(testProfile);
        testUser.setLearnerProfile(null);
        userTestRepository.save(testUser);

        LearnerProfileDTO response = request.get("/api/atlas/learner-profile", HttpStatus.OK, LearnerProfileDTO.class);
        assertThat(response).isNotNull();
        // Optionally, check for default values or expected values
        assertThat(response.feedbackDetail()).isNotNull();
        assertThat(response.feedbackFormality()).isNotNull();
        // Optionally, assert that the profile now exists in the database
        LearnerProfile createdProfile = learnerProfileRepository.findByUserElseThrow(testUser);
        assertThat(createdProfile).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_Success() throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 1, // feedbackDetail
                1, // feedbackFormality
                true);

        LearnerProfileDTO response = request.putWithResponseBody("/api/atlas/learner-profile", updateDTO, LearnerProfileDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testProfile.getId());
        assertThat(response.feedbackDetail()).isEqualTo(1);
        assertThat(response.feedbackFormality()).isEqualTo(1);
        assertThat(response.hasSetupFeedbackPreferences()).isTrue();

        // Verify the profile was actually updated in the database
        LearnerProfile updatedProfile = learnerProfileRepository.findByUserElseThrow(testUser);
        assertThat(updatedProfile.getFeedbackDetail()).isEqualTo(1);
        assertThat(updatedProfile.getFeedbackFormality()).isEqualTo(1);
        assertThat(updatedProfile.hasSetupFeedbackPreferences()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_AllFieldsAtBoundaries() throws Exception {
        // Test minimum values (all true for booleans)
        LearnerProfileDTO minDTO = new LearnerProfileDTO(testProfile.getId(), 1, // feedbackDetail
                1, // feedbackFormality
                true);

        LearnerProfileDTO minResponse = request.putWithResponseBody("/api/atlas/learner-profile", minDTO, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(minResponse.feedbackDetail()).isEqualTo(1);
        assertThat(minResponse.feedbackFormality()).isEqualTo(1);
        assertThat(minResponse.hasSetupFeedbackPreferences()).isTrue();

        // Test maximum values (all false for booleans)
        LearnerProfileDTO maxDTO = new LearnerProfileDTO(testProfile.getId(), 3, // feedbackDetail
                3, // feedbackFormality
                false);

        LearnerProfileDTO maxResponse = request.putWithResponseBody("/api/atlas/learner-profile", maxDTO, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(maxResponse.feedbackDetail()).isEqualTo(3);
        assertThat(maxResponse.feedbackFormality()).isEqualTo(3);
        assertThat(maxResponse.hasSetupFeedbackPreferences()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_ProfileNotFound() throws Exception {
        // Delete the profile to simulate a user without a profile
        learnerProfileRepository.delete(testProfile);
        testUser.setLearnerProfile(null);
        userTestRepository.save(testUser);

        LearnerProfileDTO updateDTO = new LearnerProfileDTO(999L, // Non-existent ID
                1, // feedbackDetail
                1, // feedbackFormality
                true);

        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_NoChanges() throws Exception {
        // Update with the same values
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 1, // Same as original
                1, // Same as original
                true);

        LearnerProfileDTO response = request.putWithResponseBody("/api/atlas/learner-profile", updateDTO, LearnerProfileDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.feedbackDetail()).isEqualTo(1);
        assertThat(response.feedbackFormality()).isEqualTo(1);
        assertThat(response.hasSetupFeedbackPreferences()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_SequentialUpdates() throws Exception {
        // First update
        LearnerProfileDTO firstUpdate = new LearnerProfileDTO(testProfile.getId(), 1, 1, true);

        LearnerProfileDTO firstResponse = request.putWithResponseBody("/api/atlas/learner-profile", firstUpdate, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(firstResponse.feedbackDetail()).isEqualTo(1);
        assertThat(firstResponse.feedbackFormality()).isEqualTo(1);
        assertThat(firstResponse.hasSetupFeedbackPreferences()).isTrue();

        // Second update
        LearnerProfileDTO secondUpdate = new LearnerProfileDTO(testProfile.getId(), 3, 3, false);

        LearnerProfileDTO secondResponse = request.putWithResponseBody("/api/atlas/learner-profile", secondUpdate, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(secondResponse.feedbackDetail()).isEqualTo(3);
        assertThat(secondResponse.feedbackFormality()).isEqualTo(3);
        assertThat(secondResponse.hasSetupFeedbackPreferences()).isTrue();

        // Verify final state in database
        LearnerProfile finalProfile = learnerProfileRepository.findByUserElseThrow(testUser);
        assertThat(finalProfile.getFeedbackDetail()).isEqualTo(3);
        assertThat(finalProfile.getFeedbackFormality()).isEqualTo(3);
        assertThat(finalProfile.hasSetupFeedbackPreferences()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_FeedbackDetailBelowMinimum_ReturnsBadRequest() throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 0, // Below minimum (1)
                2, // Valid feedbackFormality
                true);

        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_FeedbackDetailAboveMaximum_ReturnsBadRequest() throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 4, // Above maximum (3)
                2, // Valid feedbackFormality
                true);

        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_FeedbackFormalityBelowMinimum_ReturnsBadRequest() throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 2, // Valid feedbackDetail
                0, // Below minimum (1)
                true);

        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_FeedbackFormalityAboveMaximum_ReturnsBadRequest() throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 2, // Valid feedbackDetail
                4, // Above maximum (3)
                true);

        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_BothFieldsOutOfRange_ReturnsBadRequest() throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 0, // Below minimum (1)
                4, // Above maximum (3)
                true);

        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_ValidBoundaryValues_Success() throws Exception {
        // Test minimum values
        LearnerProfileDTO minDTO = new LearnerProfileDTO(testProfile.getId(), 1, // Minimum value
                1, // Minimum value
                true);

        LearnerProfileDTO minResponse = request.putWithResponseBody("/api/atlas/learner-profile", minDTO, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(minResponse.feedbackDetail()).isEqualTo(1);
        assertThat(minResponse.feedbackFormality()).isEqualTo(1);

        // Test maximum values
        LearnerProfileDTO maxDTO = new LearnerProfileDTO(testProfile.getId(), 3, // Maximum value
                3, // Maximum value
                false);

        LearnerProfileDTO maxResponse = request.putWithResponseBody("/api/atlas/learner-profile", maxDTO, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(maxResponse.feedbackDetail()).isEqualTo(3);
        assertThat(maxResponse.feedbackFormality()).isEqualTo(3);
    }
}
