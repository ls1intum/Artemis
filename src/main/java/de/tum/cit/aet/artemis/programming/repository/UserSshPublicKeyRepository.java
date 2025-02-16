package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;

@Profile(PROFILE_CORE)
@Repository
public interface UserSshPublicKeyRepository extends ArtemisJpaRepository<UserSshPublicKey, Long> {

    List<UserSshPublicKey> findAllByUserId(Long userId);

    Optional<UserSshPublicKey> findByKeyHash(String keyHash);

    Optional<UserSshPublicKey> findByIdAndUserId(Long keyId, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByUserId(Long userId);
}
