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

    /**
     * Clears the pending e-mail change, but only while it is the request identified by the two keys. A request that replaced it in the meantime is left alone, which a cleanup
     * that matches only the account would not do.
     *
     * @param userId       the account
     * @param currentKeyId the id of the key that was mailed to the current address
     * @param newKeyId     the id of the key that was mailed to the new address
     * @return 1 if the request was cleared, 0 if it was gone or replaced
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserRecoveryKey recoveryKey
            SET recoveryKey.pendingEmailChange.newEmail = NULL,
                recoveryKey.pendingEmailChange.originalEmail = NULL,
                recoveryKey.pendingEmailChange.credentialsChangedAt = NULL,
                recoveryKey.pendingEmailChange.currentKeyId = NULL,
                recoveryKey.pendingEmailChange.currentKeyHash = NULL,
                recoveryKey.pendingEmailChange.newKeyId = NULL,
                recoveryKey.pendingEmailChange.newKeyHash = NULL,
                recoveryKey.pendingEmailChange.requestedAt = NULL,
                recoveryKey.pendingEmailChange.currentConfirmedAt = NULL,
                recoveryKey.pendingEmailChange.newConfirmedAt = NULL
            WHERE recoveryKey.userId = :userId
                AND recoveryKey.pendingEmailChange.currentKeyId = :currentKeyId
                AND recoveryKey.pendingEmailChange.newKeyId = :newKeyId
            """)
    int clearPendingEmailChangeOfRequest(@Param("userId") long userId, @Param("currentKeyId") String currentKeyId, @Param("newKeyId") String newKeyId);

    /**
     * Creates the row of an account with nothing outstanding on it. A statement that only inserts: unlike saving a new instance, which Spring Data merges because the
     * identifier is assigned, it cannot copy empty columns over a row that another request created in the meantime, it fails on the primary key instead.
     *
     * @param userId the account
     * @return 1 if the row was created; the statement fails with a duplicate key violation when the account already has a row
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            INSERT INTO UserRecoveryKey (userId)
            VALUES (:userId)
            """)
    int insertEmptyRow(@Param("userId") long userId);

    /**
     * Sets the activation key and nothing else on the row. The other columns are left as they are, which a read followed by a save of the whole row would not do:
     * it writes back what the row held when it was read, such as a pending e-mail change that was cancelled in the meantime.
     *
     * @param userId        the account
     * @param activationKey the key
     * @return 1 if the row exists and was written, 0 if the account has no row
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserRecoveryKey recoveryKey
            SET recoveryKey.activationKey = :activationKey
            WHERE recoveryKey.userId = :userId
            """)
    int updateActivationKey(@Param("userId") long userId, @Param("activationKey") String activationKey);

    /**
     * Clears the activation key and nothing else on the row.
     *
     * @param userId the account
     * @return 1 if the row exists and was written, 0 if the account has no row
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserRecoveryKey recoveryKey
            SET recoveryKey.activationKey = NULL
            WHERE recoveryKey.userId = :userId
            """)
    int clearActivationKey(@Param("userId") long userId);

    /**
     * Sets the password reset key and nothing else on the row, with the guarantee of {@link #updateActivationKey}.
     *
     * @param userId       the account
     * @param resetKeyId   the id of the key
     * @param resetKeyHash the hash of the secret of the key
     * @param resetDate    when the key was issued
     * @return 1 if the row exists and was written, 0 if the account has no row
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserRecoveryKey recoveryKey
            SET recoveryKey.resetKeyId = :resetKeyId,
                recoveryKey.resetKeyHash = :resetKeyHash,
                recoveryKey.resetDate = :resetDate
            WHERE recoveryKey.userId = :userId
            """)
    int updateResetKey(@Param("userId") long userId, @Param("resetKeyId") String resetKeyId, @Param("resetKeyHash") String resetKeyHash, @Param("resetDate") Instant resetDate);

    /**
     * Clears the password reset key and nothing else on the row.
     *
     * @param userId the account
     * @return 1 if the row exists and was written, 0 if the account has no row
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserRecoveryKey recoveryKey
            SET recoveryKey.resetKeyId = NULL,
                recoveryKey.resetKeyHash = NULL,
                recoveryKey.resetDate = NULL
            WHERE recoveryKey.userId = :userId
            """)
    int clearResetKey(@Param("userId") long userId);

    /**
     * Sets the pending e-mail change and nothing else on the row, with the guarantee of {@link #updateActivationKey}.
     *
     * @param userId               the account
     * @param newEmail             the address the account will have once both addresses have confirmed
     * @param originalEmail        the address the account has now, where the link to the current address is mailed, or null if it has none
     * @param credentialsChangedAt when the credentials of the account last changed, as it was read for this request, or null if they never have
     * @param currentKeyId         the id of the key mailed to the current address
     * @param currentKeyHash       the hash of the secret mailed to the current address
     * @param newKeyId             the id of the key mailed to the new address
     * @param newKeyHash           the hash of the secret mailed to the new address
     * @param requestedAt          when the request was made
     * @param currentConfirmed     when the current address was confirmed, or null
     * @param newConfirmed         when the new address was confirmed, or null
     * @return 1 if the row exists and was written, 0 if the account has no row
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserRecoveryKey recoveryKey
            SET recoveryKey.pendingEmailChange.newEmail = :newEmail,
                recoveryKey.pendingEmailChange.originalEmail = :originalEmail,
                recoveryKey.pendingEmailChange.credentialsChangedAt = :credentialsChangedAt,
                recoveryKey.pendingEmailChange.currentKeyId = :currentKeyId,
                recoveryKey.pendingEmailChange.currentKeyHash = :currentKeyHash,
                recoveryKey.pendingEmailChange.newKeyId = :newKeyId,
                recoveryKey.pendingEmailChange.newKeyHash = :newKeyHash,
                recoveryKey.pendingEmailChange.requestedAt = :requestedAt,
                recoveryKey.pendingEmailChange.currentConfirmedAt = :currentConfirmed,
                recoveryKey.pendingEmailChange.newConfirmedAt = :newConfirmed
            WHERE recoveryKey.userId = :userId
            """)
    int updatePendingEmailChange(@Param("userId") long userId, @Param("newEmail") String newEmail, @Param("originalEmail") String originalEmail,
            @Param("credentialsChangedAt") Instant credentialsChangedAt, @Param("currentKeyId") String currentKeyId, @Param("currentKeyHash") String currentKeyHash,
            @Param("newKeyId") String newKeyId, @Param("newKeyHash") String newKeyHash, @Param("requestedAt") Instant requestedAt,
            @Param("currentConfirmed") Instant currentConfirmed, @Param("newConfirmed") Instant newConfirmed);

    /**
     * Clears the pending e-mail change, whichever request it is, and nothing else on the row.
     *
     * @param userId the account
     * @return 1 if the row exists and was written, 0 if the account has no row
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserRecoveryKey recoveryKey
            SET recoveryKey.pendingEmailChange.newEmail = NULL,
                recoveryKey.pendingEmailChange.originalEmail = NULL,
                recoveryKey.pendingEmailChange.credentialsChangedAt = NULL,
                recoveryKey.pendingEmailChange.currentKeyId = NULL,
                recoveryKey.pendingEmailChange.currentKeyHash = NULL,
                recoveryKey.pendingEmailChange.newKeyId = NULL,
                recoveryKey.pendingEmailChange.newKeyHash = NULL,
                recoveryKey.pendingEmailChange.requestedAt = NULL,
                recoveryKey.pendingEmailChange.currentConfirmedAt = NULL,
                recoveryKey.pendingEmailChange.newConfirmedAt = NULL
            WHERE recoveryKey.userId = :userId
            """)
    int clearPendingEmailChange(@Param("userId") long userId);

    /**
     * Removes the row of an account when nothing is outstanding on it any more.
     *
     * @param userId the account
     * @return 1 if the row was removed, 0 if it holds something or does not exist
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            DELETE FROM UserRecoveryKey recoveryKey
            WHERE recoveryKey.userId = :userId
                AND recoveryKey.activationKey IS NULL
                AND recoveryKey.resetKeyId IS NULL
                AND recoveryKey.resetKeyHash IS NULL
                AND recoveryKey.resetDate IS NULL
                AND recoveryKey.pendingEmailChange.currentKeyId IS NULL
                AND recoveryKey.pendingEmailChange.newKeyId IS NULL
            """)
    int deleteIfNothingOutstanding(@Param("userId") long userId);
}
