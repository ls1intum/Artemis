package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile.DEFAULT_PROFILE_VALUE;
import static de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile.MAX_PROFILE_VALUE;
import static de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile.MIN_PROFILE_VALUE;

import java.util.Optional;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@Lazy
@RestController
@RequestMapping("api/atlas/")
public class LearnerProfileResource {

    private static final Logger log = LoggerFactory.getLogger(LearnerProfileResource.class);

    private final UserRepository userRepository;

    private final LearnerProfileRepository learnerProfileRepository;

    public LearnerProfileResource(UserRepository userRepository, LearnerProfileRepository learnerProfileRepository) {
        this.userRepository = userRepository;
        this.learnerProfileRepository = learnerProfileRepository;
    }

    /**
     * Validates that fields are within {@link LearnerProfile#MIN_PROFILE_VALUE} and {@link LearnerProfile#MAX_PROFILE_VALUE}.
     *
     * @param value     Value of the field
     * @param fieldName Field name
     */
    private void validateProfileField(int value, String fieldName) {
        if (value < MIN_PROFILE_VALUE || value > MAX_PROFILE_VALUE) {
            String message = String.format("%s (%d) is outside valid bounds [%d, %d]", fieldName, value, MIN_PROFILE_VALUE, MAX_PROFILE_VALUE);
            throw new BadRequestAlertException(message, LearnerProfile.ENTITY_NAME, fieldName.toLowerCase() + "OutOfBounds", true);
        }
    }

    /**
     * GET learner-profile : get the {@link LearnerProfile} of the current user if it exists, otherwise create a new profile.
     *
     * @return A ResponseEntity with a status matching the validity of the request containing the profile.
     */
    @GetMapping("learner-profile")
    @EnforceAtLeastStudent
    public ResponseEntity<LearnerProfileDTO> getOrCreateLearnerProfile() {
        User user = userRepository.getUser();
        log.debug("REST request to get or create LearnerProfile of user {}", user.getLogin());

        Optional<LearnerProfile> existingProfile = learnerProfileRepository.findByUser(user);
        if (existingProfile.isPresent()) {
            return ResponseEntity.ok(LearnerProfileDTO.of(existingProfile.get()));
        }

        LearnerProfile profile = new LearnerProfile();
        profile.setUser(user);
        profile.setFeedbackDetail(DEFAULT_PROFILE_VALUE);
        profile.setFeedbackFormality(DEFAULT_PROFILE_VALUE);
        profile.setHasSetupFeedbackPreferences(false);

        user.setLearnerProfile(profile);
        userRepository.save(user);

        LearnerProfile persistedProfile = learnerProfileRepository.findByUserElseThrow(user);
        return ResponseEntity.ok(LearnerProfileDTO.of(persistedProfile));
    }

    /**
     * PUT learner-profile : update fields in the current user's {@link LearnerProfile}.
     *
     * @param learnerProfileDTO {@link LearnerProfileDTO} object from the request body.
     * @return A ResponseEntity with a status matching the validity of the request containing the updated profile.
     */
    @PutMapping(value = "learner-profile")
    @EnforceAtLeastStudent
    public ResponseEntity<LearnerProfileDTO> updateLearnerProfile(@Valid @RequestBody LearnerProfileDTO learnerProfileDTO) {
        User user = userRepository.getUser();
        log.debug("REST request to update LearnerProfile of user {}", user.getLogin());

        LearnerProfile updateProfile = learnerProfileRepository.findByUserElseThrow(user);

        validateProfileField(learnerProfileDTO.feedbackDetail(), "FeedbackDetail");
        validateProfileField(learnerProfileDTO.feedbackFormality(), "FeedbackFormality");

        updateProfile.setFeedbackDetail(learnerProfileDTO.feedbackDetail());
        updateProfile.setFeedbackFormality(learnerProfileDTO.feedbackFormality());

        // Set the flag to true when the user updates their preferences
        updateProfile.setHasSetupFeedbackPreferences(true);

        LearnerProfile result = learnerProfileRepository.save(updateProfile);
        return ResponseEntity.ok(LearnerProfileDTO.of(result));
    }
}
