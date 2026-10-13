package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.UserRecoveryKey;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserRecoveryKeyRepository extends ArtemisJpaRepository<UserRecoveryKey, Long> {

    Optional<UserRecoveryKey> findByUserId(long userId);

    Optional<UserRecoveryKey> findByActivationKey(String activationKey);

    Optional<UserRecoveryKey> findByResetKeyId(String resetKeyId);

    Optional<UserRecoveryKey> findByPendingEmailChangeCurrentKeyId(String keyId);

    Optional<UserRecoveryKey> findByPendingEmailChangeNewKeyId(String keyId);

    /**
     * Records that the owner of the current address confirmed, as one statement that only matches while the request with this key still exists and has not been confirmed
     * from this side yet. A cancelled or replaced request is therefore never written back, which a read followed by a save of the whole row would do.
     *
     * @param keyId the id of the key mailed to the current address
     * @param now   when the address was confirmed
     * @return 1 if the confirmation was recorded, 0 if the request is gone, replaced or already confirmed from this side
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserRecoveryKey recoveryKey
            SET recoveryKey.pendingEmailChange.currentConfirmedAt = :now
            WHERE recoveryKey.pendingEmailChange.currentKeyId = :keyId
                AND recoveryKey.pendingEmailChange.currentConfirmedAt IS NULL
            """)
    int confirmCurrentAddress(@Param("keyId") String keyId, @Param("now") Instant now);

    /**
     * Records that the owner of the new address confirmed, with the same guarantees as {@link #confirmCurrentAddress}.
     *
     * @param keyId the id of the key mailed to the new address
     * @param now   when the address was confirmed
     * @return 1 if the confirmation was recorded, 0 if the request is gone, replaced or already confirmed from this side
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserRecoveryKey recoveryKey
            SET recoveryKey.pendingEmailChange.newConfirmedAt = :now
            WHERE recoveryKey.pendingEmailChange.newKeyId = :keyId
                AND recoveryKey.pendingEmailChange.newConfirmedAt IS NULL
            """)
    int confirmNewAddress(@Param("keyId") String keyId, @Param("now") Instant now);
}
