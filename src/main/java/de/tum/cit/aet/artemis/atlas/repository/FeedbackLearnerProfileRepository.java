package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.profile.FeedbackLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface FeedbackLearnerProfileRepository extends ArtemisJpaRepository<FeedbackLearnerProfile, Long> {

    Optional<FeedbackLearnerProfile> findByLearnerProfile(LearnerProfile learnerProfile);

    default FeedbackLearnerProfile findByLearnerProfileElseThrow(LearnerProfile learnerProfile) {
        return getValueElseThrow(findByLearnerProfile(learnerProfile));
    }
}
