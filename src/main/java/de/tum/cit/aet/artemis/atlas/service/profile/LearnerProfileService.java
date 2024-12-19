package de.tum.cit.aet.artemis.atlas.service.profile;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Profile(PROFILE_CORE)
@Service
public class LearnerProfileService {

    private final LearnerProfileRepository learnerProfileRepository;

    private final UserRepository userRepository;

    public LearnerProfileService(LearnerProfileRepository learnerProfileRepository, UserRepository userRepository) {
        this.learnerProfileRepository = learnerProfileRepository;
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

    /**
     * Delete the learner profile of a user
     *
     * @param user the user for which the profile is deleted
     */
    public void deleteProfile(User user) {
        learnerProfileRepository.deleteByUser(user);
    }
}
