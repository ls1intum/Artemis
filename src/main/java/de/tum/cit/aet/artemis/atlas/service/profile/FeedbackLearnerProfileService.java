package de.tum.cit.aet.artemis.atlas.service.profile;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.profile.FeedbackLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.FeedbackLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.User;

@Profile(PROFILE_CORE)
@Service
public class FeedbackLearnerProfileService {

    private final FeedbackLearnerProfileRepository feedbackLearnerProfileRepository;

    private final LearnerProfileRepository learnerProfileRepository;

    public FeedbackLearnerProfileService(FeedbackLearnerProfileRepository feedbackLearnerProfileRepository, LearnerProfileRepository learnerProfileRepository) {
        this.feedbackLearnerProfileRepository = feedbackLearnerProfileRepository;
        this.learnerProfileRepository = learnerProfileRepository;
    }

    /**
     * Get the feedback learner profile for a user
     *
     * @param user the user for which to get the profile
     * @return the feedback learner profile
     */
    public FeedbackLearnerProfile getFeedbackProfile(User user) {
        LearnerProfile learnerProfile = learnerProfileRepository.findByUserElseThrow(user);
        return feedbackLearnerProfileRepository.findByLearnerProfile(learnerProfile).orElseGet(() -> createFeedbackProfile(learnerProfile));
    }

    /**
     * Create a feedback learner profile for a learner profile
     *
     * @param learnerProfile the learner profile for which to create the feedback profile
     * @return the created feedback learner profile
     */
    private FeedbackLearnerProfile createFeedbackProfile(LearnerProfile learnerProfile) {
        FeedbackLearnerProfile profile = new FeedbackLearnerProfile();
        profile.setLearnerProfile(learnerProfile);
        // Set default values
        profile.setPracticalVsTheoretical(0);
        profile.setCreativeVsFocused(0);
        profile.setFollowUpVsSummary(0);
        profile.setBriefVsDetailed(2);
        return feedbackLearnerProfileRepository.save(profile);
    }

    /**
     * Update a feedback learner profile
     *
     * @param profile the profile to update
     * @return the updated profile
     */
    public FeedbackLearnerProfile updateFeedbackProfile(FeedbackLearnerProfile profile) {
        return feedbackLearnerProfileRepository.save(profile);
    }
}
