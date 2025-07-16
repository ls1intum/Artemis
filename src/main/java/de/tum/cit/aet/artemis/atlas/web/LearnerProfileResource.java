package de.tum.cit.aet.artemis.atlas.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
     * GET learner-profile : get the {@link LearnerProfile} of the current user.
     * If no profile exists for the current user, a BadRequestAlertException is thrown.
     *
     * @return A ResponseEntity with a status matching the validity of the request containing the profile.
     */
    @GetMapping("learner-profile")
    @EnforceAtLeastStudent
    public ResponseEntity<LearnerProfileDTO> getOrCreateLearnerProfile() {
        User user = userRepository.getUser();
        log.debug("REST request to get or create LearnerProfile of user {}", user.getLogin());

        if (learnerProfileRepository.findByUser(user).isPresent()) {
            return ResponseEntity.ok(LearnerProfileDTO.of(learnerProfileRepository.findByUserElseThrow(user)));
        }

        LearnerProfile profile = new LearnerProfile();
        profile.setUser(user);
        profile.setBriefFeedback(false);
        profile.setFormalFeedback(false);
        profile.setHasSetupFeedbackPreferences(false);

        user.setLearnerProfile(profile);
        userRepository.save(user);

        LearnerProfile persistedProfile = learnerProfileRepository.findByUser(user).orElseThrow();
        return ResponseEntity.ok(LearnerProfileDTO.of(persistedProfile));
    }

    /**
     * PUT learner-profiles/{learnerProfileId} : update fields in a {@link LearnerProfile}.
     *
     * @param learnerProfileDTO {@link LearnerProfileDTO} object from the request body.
     * @return A ResponseEntity with a status matching the validity of the request containing the updated profile.
     */
    @PutMapping(value = "learner-profile")
    @EnforceAtLeastStudent
    public ResponseEntity<LearnerProfileDTO> updateLearnerProfile(@RequestBody LearnerProfileDTO learnerProfileDTO) {
        User user = userRepository.getUser();
        log.debug("REST request to update LearnerProfile of user {}", user.getLogin());

        LearnerProfile updateProfile = learnerProfileRepository.findByUserElseThrow(user);

        updateProfile.setBriefFeedback(learnerProfileDTO.isBriefFeedback());
        updateProfile.setFormalFeedback(learnerProfileDTO.isFormalFeedback());

        // Set the flag to true when the user updates their preferences
        updateProfile.setHasSetupFeedbackPreferences(true);

        LearnerProfile result = learnerProfileRepository.save(updateProfile);
        return ResponseEntity.ok(LearnerProfileDTO.of(result));
    }

    /**
     * POST learner-profiles/{learnerProfileId} : create a {@link LearnerProfile}.
     *
     * @param learnerProfileDTO {@link LearnerProfileDTO} object from the request body.
     * @return A ResponseEntity with a status matching the validity of the request containing the created profile.
     */
    @PostMapping(value = "learner-profile")
    @EnforceAtLeastStudent
    public ResponseEntity<LearnerProfileDTO> createLearnerProfile(@RequestBody LearnerProfileDTO learnerProfileDTO) {
        User user = userRepository.getUser();
        log.debug("REST request to create LearnerProfile of user {}", user.getLogin());

        if (learnerProfileRepository.findByUser(user).isPresent()) {
            throw new BadRequestAlertException("LearnerProfile already exists", LearnerProfile.ENTITY_NAME, "learnerProfileAlreadyExists", true);
        }

        LearnerProfile profile = new LearnerProfile();
        profile.setUser(user);
        profile.setBriefFeedback(learnerProfileDTO.isBriefFeedback());
        profile.setFormalFeedback(learnerProfileDTO.isFormalFeedback());
        profile.setHasSetupFeedbackPreferences(true);

        user.setLearnerProfile(profile);
        userRepository.save(user);

        LearnerProfile persistedProfile = learnerProfileRepository.findByUser(user).orElseThrow();
        return ResponseEntity.ok(LearnerProfileDTO.of(persistedProfile));
    }
}
