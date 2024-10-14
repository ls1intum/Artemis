package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.UserPublicSshKey;

@Profile(PROFILE_CORE)
@Repository
public interface UserPublicSshKeyRepository extends ArtemisJpaRepository<UserPublicSshKey, Long> {

    List<UserPublicSshKey> findAllByUserId(Long userId);
}
