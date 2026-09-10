package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.account.domain.UserRecoveryKey;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserRecoveryKeyRepository extends ArtemisJpaRepository<UserRecoveryKey, Long> {

    Optional<UserRecoveryKey> findByUserId(long userId);

    Optional<UserRecoveryKey> findByActivationKey(String activationKey);

    Optional<UserRecoveryKey> findByResetKey(String resetKey);
}
