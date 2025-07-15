package de.tum.cit.aet.artemis.atlas.service.profile;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@ConditionalOnProperty(name = "artemis.atlas.enabled", havingValue = "true")
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
