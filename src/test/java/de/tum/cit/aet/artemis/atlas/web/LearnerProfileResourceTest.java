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
        testProfile.setBriefFeedback(true);
        testProfile.setFormalFeedback(true);
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
        assertThat(response.isBriefFeedback()).isTrue();
        assertThat(response.isFormalFeedback()).isTrue();
        assertThat(response.hasSetupFeedbackPreferences()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLearnerProfile_ProfileNotFound() throws Exception {
        // Delete the profile to simulate a user without a profile
        learnerProfileRepository.delete(testProfile);
        testUser.setLearnerProfile(null);
        userTestRepository.save(testUser);

        // Should throw an exception when profile doesn't exist
        request.get("/api/atlas/learner-profile", HttpStatus.NOT_FOUND, LearnerProfileDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_Success() throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), true, // isBriefFeedback
                true, // isFormalFeedback
                true);

        LearnerProfileDTO response = request.putWithResponseBody("/api/atlas/learner-profile", updateDTO, LearnerProfileDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testProfile.getId());
        assertThat(response.isBriefFeedback()).isTrue();
        assertThat(response.isFormalFeedback()).isTrue();
        assertThat(response.hasSetupFeedbackPreferences()).isTrue();

        // Verify the profile was actually updated in the database
        LearnerProfile updatedProfile = learnerProfileRepository.findByUserElseThrow(testUser);
        assertThat(updatedProfile.isBriefFeedback()).isTrue();
        assertThat(updatedProfile.isFormalFeedback()).isTrue();
        assertThat(updatedProfile.hasSetupFeedbackPreferences()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_AllFieldsAtBoundaries() throws Exception {
        // Test minimum values (all true for booleans)
        LearnerProfileDTO minDTO = new LearnerProfileDTO(testProfile.getId(), true, // isBriefFeedback
                true, // isFormalFeedback
                true);

        LearnerProfileDTO minResponse = request.putWithResponseBody("/api/atlas/learner-profile", minDTO, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(minResponse.isBriefFeedback()).isTrue();
        assertThat(minResponse.isFormalFeedback()).isTrue();
        assertThat(minResponse.hasSetupFeedbackPreferences()).isTrue();

        // Test maximum values (all false for booleans)
        LearnerProfileDTO maxDTO = new LearnerProfileDTO(testProfile.getId(), false, // isBriefFeedback
                false, // isFormalFeedback
                false);

        LearnerProfileDTO maxResponse = request.putWithResponseBody("/api/atlas/learner-profile", maxDTO, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(maxResponse.isBriefFeedback()).isFalse();
        assertThat(maxResponse.isFormalFeedback()).isFalse();
        assertThat(maxResponse.hasSetupFeedbackPreferences()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_ProfileNotFound() throws Exception {
        // Delete the profile to simulate a user without a profile
        learnerProfileRepository.delete(testProfile);
        testUser.setLearnerProfile(null);
        userTestRepository.save(testUser);

        LearnerProfileDTO updateDTO = new LearnerProfileDTO(999L, // Non-existent ID
                true, true, true);

        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_NoChanges() throws Exception {
        // Update with the same values
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), true, // Same as original
                true, // Same as original
                true);

        LearnerProfileDTO response = request.putWithResponseBody("/api/atlas/learner-profile", updateDTO, LearnerProfileDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.isBriefFeedback()).isTrue();
        assertThat(response.isFormalFeedback()).isTrue();
        assertThat(response.hasSetupFeedbackPreferences()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_SequentialUpdates() throws Exception {
        // First update
        LearnerProfileDTO firstUpdate = new LearnerProfileDTO(testProfile.getId(), true, true, true);

        LearnerProfileDTO firstResponse = request.putWithResponseBody("/api/atlas/learner-profile", firstUpdate, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(firstResponse.isBriefFeedback()).isTrue();
        assertThat(firstResponse.isFormalFeedback()).isTrue();
        assertThat(firstResponse.hasSetupFeedbackPreferences()).isTrue();

        // Second update
        LearnerProfileDTO secondUpdate = new LearnerProfileDTO(testProfile.getId(), false, false, false);

        LearnerProfileDTO secondResponse = request.putWithResponseBody("/api/atlas/learner-profile", secondUpdate, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(secondResponse.isBriefFeedback()).isFalse();
        assertThat(secondResponse.isFormalFeedback()).isFalse();
        assertThat(secondResponse.hasSetupFeedbackPreferences()).isFalse();

        // Verify final state in database
        LearnerProfile finalProfile = learnerProfileRepository.findByUserElseThrow(testUser);
        assertThat(finalProfile.isBriefFeedback()).isFalse();
        assertThat(finalProfile.isFormalFeedback()).isFalse();
        assertThat(finalProfile.hasSetupFeedbackPreferences()).isFalse();
    }
}
