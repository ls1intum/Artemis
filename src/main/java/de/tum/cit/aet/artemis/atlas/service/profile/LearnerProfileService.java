package de.tum.cit.aet.artemis.atlas.service.profile;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class LearnerProfileService {

    private final UserRepository userRepository;

    private final LearnerProfileRepository learnerProfileRepository;

    public LearnerProfileService(UserRepository userRepository, LearnerProfileRepository learnerProfileRepository) {
        this.userRepository = userRepository;
        this.learnerProfileRepository = learnerProfileRepository;
    }

    /**
     * Create a learner profile for a user and saves it in the database
     * If a profile already exists for the user, returns the existing profile
     *
     * @param user the user for which the profile is created
     * @return Saved LearnerProfile
     */
    public LearnerProfile createProfile(User user) {
        // Check if profile already exists
        Optional<LearnerProfile> existingProfile = learnerProfileRepository.findByUser(user);
        if (existingProfile.isPresent()) {
            return existingProfile.get();
        }

        var profile = new LearnerProfile();
        profile.setUser(user);
        user.setLearnerProfile(profile);
        userRepository.save(user);
        return profile;
    }

    /**
     * Get or create a learner profile for a user
     *
     * @param user the user for which the profile is retrieved or created
     * @return Saved LearnerProfile
     */
    public LearnerProfile getOrCreateLearnerProfile(User user) {
        return learnerProfileRepository.findByUser(user).orElseGet(() -> createProfile(user));
    }
}
