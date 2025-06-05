package de.tum.cit.aet.artemis.atlas.service.profile;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Conditional(AtlasEnabled.class)
@Service
public class LearnerProfileService {

    private final UserRepository userRepository;

    public LearnerProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Create a learner profile for a user and saves it in the database
     *
     * @param user the user for which the profile is created
     */
    public void createProfile(User user) {
        var profile = new LearnerProfile();
        profile.setUser(user);
        user.setLearnerProfile(profile);
        userRepository.save(user);
    }
}
