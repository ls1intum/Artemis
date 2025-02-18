package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface LearnerProfileRepository extends ArtemisJpaRepository<LearnerProfile, Long> {

    Optional<LearnerProfile> findByUser(User user);

    default LearnerProfile findByUserElseThrow(User user) {
        return getValueElseThrow(findByUser(user));
    }

    @Transactional // ok because of delete
    @Modifying
    void deleteByUser(User user);
}
