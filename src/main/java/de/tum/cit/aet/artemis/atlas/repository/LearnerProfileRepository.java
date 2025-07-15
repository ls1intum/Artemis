package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@ConditionalOnProperty(name = "artemis.atlas.enabled", havingValue = "true")
@Repository
public interface LearnerProfileRepository extends ArtemisJpaRepository<LearnerProfile, Long> {

    Optional<LearnerProfile> findByUser(User user);

    default LearnerProfile findByUserElseThrow(User user) {
        return getValueElseThrow(findByUser(user));
    }

    Set<LearnerProfile> findAllByUserIn(Set<User> users);

    @Transactional // ok because of delete
    @Modifying
    void deleteByUser(User user);
}
