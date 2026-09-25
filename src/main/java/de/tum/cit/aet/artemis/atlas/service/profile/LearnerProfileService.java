package de.tum.cit.aet.artemis.atlas.service.profile;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class LearnerProfileService {

    private final LearnerProfileRepository learnerProfileRepository;

    public LearnerProfileService(LearnerProfileRepository learnerProfileRepository) {
        this.learnerProfileRepository = learnerProfileRepository;
    }

    /**
     * Create a learner profile for a user and saves it in the database
     *
     * @param user the user for which the profile is created
     * @return Saved LearnerProfile
     */
    public LearnerProfile createProfile(User user) {
        var profile = new LearnerProfile();
        profile.setUser(user);
        // The profile holds the key, so it is saved on its own. Saving the account instead would cascade into a merge
        // copy and leave this object without an id.
        return learnerProfileRepository.save(profile);
    }

    /**
     * Get or create a learner profile for a user.
     * <p>
     * Concurrent calls for the same user can both find no profile and both try to create one. The unique constraint on the user lets only one of them
     * succeed, so the other one returns the profile created in the meantime.
     *
     * @param user the user for which the profile is retrieved or created
     * @return Saved LearnerProfile
     */
    public LearnerProfile getOrCreateLearnerProfile(User user) {
        Optional<LearnerProfile> existingProfile = learnerProfileRepository.findByUser(user);
        if (existingProfile.isPresent()) {
            return existingProfile.get();
        }
        try {
            return createProfile(user);
        }
        catch (DataIntegrityViolationException e) {
            // Only a profile that exists by now was created concurrently; any other integrity violation is a real error
            return learnerProfileRepository.findByUser(user).orElseThrow(() -> e);
        }
    }
}
