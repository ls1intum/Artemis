package de.tum.cit.aet.artemis.atlas.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
        testProfile.setFeedbackAlternativeStandard(2);
        testProfile.setFeedbackFollowupSummary(2);
        testProfile.setFeedbackBriefDetailed(2);
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
        assertThat(response.feedbackAlternativeStandard()).isEqualTo(2);
        assertThat(response.feedbackFollowupSummary()).isEqualTo(2);
        assertThat(response.feedbackBriefDetailed()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLearnerProfile_CreatesNewProfileWhenNotFound() throws Exception {
        // Delete the profile to simulate a user without a profile
        learnerProfileRepository.delete(testProfile);
        testUser.setLearnerProfile(null);
        userTestRepository.save(testUser);

        // Should create a new profile when one doesn't exist
        LearnerProfileDTO response = request.get("/api/atlas/learner-profile", HttpStatus.OK, LearnerProfileDTO.class);

        assertThat(response).isNotNull();
        assertThat(response.feedbackAlternativeStandard()).isEqualTo(2);
        assertThat(response.feedbackFollowupSummary()).isEqualTo(2);
        assertThat(response.feedbackBriefDetailed()).isEqualTo(2);

        // Verify a new profile was created in the database
        LearnerProfile newProfile = learnerProfileRepository.findByUserElseThrow(testUser);
        assertThat(newProfile.getId()).isNotNull();
        assertThat(newProfile.getFeedbackAlternativeStandard()).isEqualTo(2);
        assertThat(newProfile.getFeedbackFollowupSummary()).isEqualTo(2);
        assertThat(newProfile.getFeedbackBriefDetailed()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_Success() throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 3, // feedbackAlternativeStandard
                1, // feedbackFollowupSummary
                2  // feedbackBriefDetailed
        );

        LearnerProfileDTO response = request.putWithResponseBody("/api/atlas/learner-profile", updateDTO, LearnerProfileDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testProfile.getId());
        assertThat(response.feedbackAlternativeStandard()).isEqualTo(3);
        assertThat(response.feedbackFollowupSummary()).isEqualTo(1);
        assertThat(response.feedbackBriefDetailed()).isEqualTo(2);

        // Verify the profile was actually updated in the database
        LearnerProfile updatedProfile = learnerProfileRepository.findByUserElseThrow(testUser);
        assertThat(updatedProfile.getFeedbackAlternativeStandard()).isEqualTo(3);
        assertThat(updatedProfile.getFeedbackFollowupSummary()).isEqualTo(1);
        assertThat(updatedProfile.getFeedbackBriefDetailed()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_AllFieldsAtBoundaries() throws Exception {
        // Test minimum values
        LearnerProfileDTO minDTO = new LearnerProfileDTO(testProfile.getId(), 1, // feedbackAlternativeStandard - minimum
                1, // feedbackFollowupSummary - minimum
                1  // feedbackBriefDetailed - minimum
        );

        LearnerProfileDTO minResponse = request.putWithResponseBody("/api/atlas/learner-profile", minDTO, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(minResponse.feedbackAlternativeStandard()).isEqualTo(1);
        assertThat(minResponse.feedbackFollowupSummary()).isEqualTo(1);
        assertThat(minResponse.feedbackBriefDetailed()).isEqualTo(1);

        // Test maximum values
        LearnerProfileDTO maxDTO = new LearnerProfileDTO(testProfile.getId(), 3, // feedbackAlternativeStandard - maximum
                3, // feedbackFollowupSummary - maximum
                3  // feedbackBriefDetailed - maximum
        );

        LearnerProfileDTO maxResponse = request.putWithResponseBody("/api/atlas/learner-profile", maxDTO, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(maxResponse.feedbackAlternativeStandard()).isEqualTo(3);
        assertThat(maxResponse.feedbackFollowupSummary()).isEqualTo(3);
        assertThat(maxResponse.feedbackBriefDetailed()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_ProfileNotFound() throws Exception {
        // Delete the profile to simulate a user without a profile
        learnerProfileRepository.delete(testProfile);
        testUser.setLearnerProfile(null);
        userTestRepository.save(testUser);

        LearnerProfileDTO updateDTO = new LearnerProfileDTO(999L, // Non-existent ID
                2, 2, 2);

        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_MultipleInvalidFields() throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 0, // feedbackAlternativeStandard - below minimum
                4, // feedbackFollowupSummary - above maximum
                0  // feedbackBriefDetailed - below minimum
        );

        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_NoChanges() throws Exception {
        // Update with the same values
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), 2, // Same as original
                2, // Same as original
                2  // Same as original
        );

        LearnerProfileDTO response = request.putWithResponseBody("/api/atlas/learner-profile", updateDTO, LearnerProfileDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.feedbackAlternativeStandard()).isEqualTo(2);
        assertThat(response.feedbackFollowupSummary()).isEqualTo(2);
        assertThat(response.feedbackBriefDetailed()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_SequentialUpdates() throws Exception {
        // First update
        LearnerProfileDTO firstUpdate = new LearnerProfileDTO(testProfile.getId(), 3, 1, 2);

        LearnerProfileDTO firstResponse = request.putWithResponseBody("/api/atlas/learner-profile", firstUpdate, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(firstResponse.feedbackAlternativeStandard()).isEqualTo(3);
        assertThat(firstResponse.feedbackFollowupSummary()).isEqualTo(1);
        assertThat(firstResponse.feedbackBriefDetailed()).isEqualTo(2);

        // Second update
        LearnerProfileDTO secondUpdate = new LearnerProfileDTO(testProfile.getId(), 1, 3, 1);

        LearnerProfileDTO secondResponse = request.putWithResponseBody("/api/atlas/learner-profile", secondUpdate, LearnerProfileDTO.class, HttpStatus.OK);
        assertThat(secondResponse.feedbackAlternativeStandard()).isEqualTo(1);
        assertThat(secondResponse.feedbackFollowupSummary()).isEqualTo(3);
        assertThat(secondResponse.feedbackBriefDetailed()).isEqualTo(1);

        // Verify final state in database
        LearnerProfile finalProfile = learnerProfileRepository.findByUserElseThrow(testUser);
        assertThat(finalProfile.getFeedbackAlternativeStandard()).isEqualTo(1);
        assertThat(finalProfile.getFeedbackFollowupSummary()).isEqualTo(3);
        assertThat(finalProfile.getFeedbackBriefDetailed()).isEqualTo(1);
    }

    // Parameterized test for invalid values (too low/too high) for each field
    static Stream<Arguments> invalidFieldValues() {
        return Stream.of(
                // feedbackAlternativeStandard too low/high
                Arguments.of(0, 2, 2), // too low
                Arguments.of(4, 2, 2), // too high
                // feedbackFollowupSummary too low/high
                Arguments.of(2, 0, 2), // too low
                Arguments.of(2, 4, 2), // too high
                // feedbackBriefDetailed too low/high
                Arguments.of(2, 2, 0), // too low
                Arguments.of(2, 2, 4)  // too high
        );
    }

    @ParameterizedTest
    @MethodSource("invalidFieldValues")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLearnerProfile_InvalidFieldValues(int feedbackAlternativeStandard, int feedbackFollowupSummary, int feedbackBriefDetailed) throws Exception {
        LearnerProfileDTO updateDTO = new LearnerProfileDTO(testProfile.getId(), feedbackAlternativeStandard, feedbackFollowupSummary, feedbackBriefDetailed);
        request.put("/api/atlas/learner-profile", updateDTO, HttpStatus.BAD_REQUEST);
    }
}
