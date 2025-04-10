package de.tum.cit.aet.artemis.atlas.web;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.LearnerProfileDTO;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

@Conditional(AtlasEnabled.class)
@RestController
@RequestMapping("api/atlas/")
public class LearnerProfileResource {

    private static final int MIN_PROFILE_VALUE = 1;

    private static final int MAX_PROFILE_VALUE = 5;

    private final UserRepository userRepository;

    private final LearnerProfileRepository learnerProfileRepository;

    public LearnerProfileResource(UserRepository userRepository, LearnerProfileRepository learnerProfileRepository) {
        this.userRepository = userRepository;
        this.learnerProfileRepository = learnerProfileRepository;
    }

    /**
     * Validates that fields are within {@link #MIN_PROFILE_VALUE} and {@link #MAX_PROFILE_VALUE}.
     *
     * @param value     Value of the field
     * @param fieldName Field name
     */
    private void validateProfileField(int value, String fieldName) {
        if (value < MIN_PROFILE_VALUE || value > MAX_PROFILE_VALUE) {
            throw new BadRequestAlertException(fieldName + " field is outside valid bounds", LearnerProfile.ENTITY_NAME, fieldName.toLowerCase() + "OutOfBounds", true);
        }
    }

    @GetMapping("learner-profiles")
    @EnforceAtLeastStudent
    public ResponseEntity<LearnerProfileDTO> getLearnerProfile() {
        User user = userRepository.getUser();
        LearnerProfile profile = learnerProfileRepository.findByUserElseThrow(user);
        return ResponseEntity.ok(LearnerProfileDTO.of(profile));
    }

    /**
     * PUT /learner-profiles/{learnerProfileId} : update fields in a {@link LearnerProfile}.
     *
     * @param learnerProfileId  ID of the LearnerProfile
     * @param learnerProfileDTO {@link LearnerProfileDTO} object from the request body.
     * @return A ResponseEntity with a status matching the validity of the request containing the updated profile.
     */
    @PutMapping(value = "learner-profiles/{learnerProfileId}")
    @EnforceAtLeastStudent
    public ResponseEntity<LearnerProfileDTO> updateLearnerProfile(@PathVariable long learnerProfileId, @RequestBody LearnerProfileDTO learnerProfileDTO) {
        User user = userRepository.getUser();

        if (learnerProfileDTO.id() != learnerProfileId) {
            throw new BadRequestAlertException("Provided learnerProfileId does not match learnerProfile.", LearnerProfile.ENTITY_NAME, "objectDoesNotMatchId", true);
        }

        Optional<LearnerProfile> optionalLearnerProfile = learnerProfileRepository.findByUser(user);

        if (optionalLearnerProfile.isEmpty()) {
            throw new BadRequestAlertException("LearnerProfile not found.", LearnerProfile.ENTITY_NAME, "LearnerProfileNotFound", true);
        }

        validateProfileField(learnerProfileDTO.feedbackPracticalTheoretical(), "FeedbackPracticalTheoretical");
        validateProfileField(learnerProfileDTO.feedbackCreativeGuidance(), "FeedbackCreativeGuidance");
        validateProfileField(learnerProfileDTO.feedbackFollowupSummary(), "FeedbackFollowupSummary");
        validateProfileField(learnerProfileDTO.feedbackBriefDetailed(), "FeedbackBriefDetailed");

        LearnerProfile updateProfile = optionalLearnerProfile.get();
        updateProfile.setFeedbackPracticalTheoretical(learnerProfileDTO.feedbackPracticalTheoretical());
        updateProfile.setFeedbackCreativeGuidance(learnerProfileDTO.feedbackCreativeGuidance());
        updateProfile.setFeedbackFollowupSummary(learnerProfileDTO.feedbackFollowupSummary());
        updateProfile.setFeedbackBriefDetailed(learnerProfileDTO.feedbackBriefDetailed());

        LearnerProfile result = learnerProfileRepository.save(updateProfile);
        return ResponseEntity.ok(LearnerProfileDTO.of(result));
    }
}
