package de.tum.cit.aet.artemis.atlas.service.profile;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.User;

@Profile(PROFILE_CORE)
@Service
public class LearnerProfileService {

    private final LearnerProfileRepository learnerProfileRepository;

    public LearnerProfileService(LearnerProfileRepository learnerProfileRepository) {
        this.learnerProfileRepository = learnerProfileRepository;
    }

    public LearnerProfile createProfile(User user) {
        var profile = new LearnerProfile();
        profile.setUser(user);
        return learnerProfileRepository.save(profile);
    }

    public void deleteProfile(User user) {
        learnerProfileRepository.deleteByUser(user);
    }
}
