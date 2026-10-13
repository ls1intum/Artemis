package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.domain.PendingEmailChange;
import de.tum.cit.aet.artemis.account.domain.UserRecoveryKey;
import de.tum.cit.aet.artemis.account.repository.UserRecoveryKeyRepository;

/**
 * Owns the activation and password-reset keys of an account and the e-mail change it is waiting to have confirmed, which live
 * in {@code user_recovery_key} rather than on the user row.
 * <p>
 * Callers go through this service so that "nothing is outstanding" is resolved in one place: a row exists only while a
 * key is pending, and a row that has become empty is deleted rather than kept as a row of nulls.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserRecoveryKeyService {

    private final UserRecoveryKeyRepository userRecoveryKeyRepository;

    public UserRecoveryKeyService(UserRecoveryKeyRepository userRecoveryKeyRepository) {
        this.userRecoveryKeyRepository = userRecoveryKeyRepository;
    }

    /**
     * The activation key the account currently has outstanding, or null when it has none.
     *
     * @param userId the account
     * @return the activation key, or null
     */
    @Nullable
    public String findActivationKey(long userId) {
        return userRecoveryKeyRepository.findByUserId(userId).map(UserRecoveryKey::getActivationKey).orElse(null);
    }

    /**
     * The reset key id the account currently has outstanding, or null when it has none.
     *
     * @param userId the account
     * @return the reset key id, or null
     */
    @Nullable
    public String findResetKeyId(long userId) {
        return userRecoveryKeyRepository.findByUserId(userId).map(UserRecoveryKey::getResetKeyId).orElse(null);
    }

    /**
     * The hashed reset key the account currently has outstanding, or null when it has none.
     *
     * @param userId the account
     * @return the hashed reset key secret, or null
     */
    @Nullable
    public String findResetKeyHash(long userId) {
        return userRecoveryKeyRepository.findByUserId(userId).map(UserRecoveryKey::getResetKeyHash).orElse(null);
    }

    /**
     * Stores an activation key for the account, replacing any previous one.
     *
     * @param userId        the account
     * @param activationKey the key to store
     */
    public void storeActivationKey(long userId, String activationKey) {
        saveHandlingConcurrentInsert(userId, row -> row.setActivationKey(activationKey));
    }

    /**
     * Stores a password-reset key and the moment it was issued, replacing any previous one.
     *
     * @param userId       the account
     * @param resetKeyId   the id of the key to store
     * @param resetKeyHash the hash of the keys secret to store
     * @param resetDate    when the key was issued, which is what bounds its validity
     */
    public void storeResetKey(long userId, String resetKeyId, String resetKeyHash, Instant resetDate) {
        saveHandlingConcurrentInsert(userId, row -> {
            row.setResetKeyId(resetKeyId);
            row.setResetKeyHash(resetKeyHash);
            row.setResetDate(resetDate);
        });
    }

    /**
     * Finds the account awaiting activation with the given key.
     *
     * @param activationKey the key presented by the caller
     * @return the id of the account, or empty if no account has that key outstanding
     */
    public Optional<Long> findUserIdByActivationKey(@Nullable String activationKey) {
        if (!StringUtils.hasText(activationKey)) {
            // A derived query turns a null argument into `IS NULL`, which would match a row that holds only a reset key.
            // Nothing may be redeemed by presenting no key at all.
            return Optional.empty();
        }
        return userRecoveryKeyRepository.findByActivationKey(activationKey).map(UserRecoveryKey::getUserId);
    }

    /**
     * Finds the data for the given reset key id.
     *
     * @param resetKeyId the id of the reset key
     * @return the pending row, or empty if no account has that key outstanding
     */
    public Optional<UserRecoveryKey> findByResetKeyId(String resetKeyId) {
        return userRecoveryKeyRepository.findByResetKeyId(resetKeyId);
    }

    /**
     * The e-mail change the account has requested and that is still waiting to be confirmed.
     *
     * @param userId the account
     * @return the pending change, or empty if none is pending
     */
    public Optional<PendingEmailChange> findPendingEmailChange(long userId) {
        return userRecoveryKeyRepository.findByUserId(userId).map(UserRecoveryKey::getPendingEmailChange);
    }

    /**
     * Stores an e-mail change the account is waiting to have confirmed, replacing any previous one.
     *
     * @param userId the account
     * @param change the change to store
     */
    public void storePendingEmailChange(long userId, PendingEmailChange change) {
        saveHandlingConcurrentInsert(userId, row -> row.setPendingEmailChange(change));
    }

    /**
     * Finds the account whose pending e-mail change one of the given keys belongs to.
     * <p>
     * A key id belongs to the address it was mailed to, so the same id never matches both lookups.
     *
     * @param keyId the id of a key mailed to the current or the new address
     * @return the row holding the pending change, or empty if no pending change has that key
     */
    public Optional<UserRecoveryKey> findByEmailChangeKeyId(String keyId) {
        return userRecoveryKeyRepository.findByPendingEmailChangeCurrentKeyId(keyId).or(() -> userRecoveryKeyRepository.findByPendingEmailChangeNewKeyId(keyId));
    }

    /**
     * Records that one address of a pending e-mail change was confirmed. Does nothing if the request was cancelled or replaced in the meantime, or if that address was
     * confirmed before, which is why the caller reads the request again afterwards instead of trusting what it read before.
     *
     * @param keyId          the id of the key from the mail
     * @param currentAddress whether the key was mailed to the current address, otherwise to the new one
     */
    public void confirmEmailChangeAddress(String keyId, boolean currentAddress) {
        Instant now = Instant.now();
        if (currentAddress) {
            userRecoveryKeyRepository.confirmCurrentAddress(keyId, now);
        }
        else {
            userRecoveryKeyRepository.confirmNewAddress(keyId, now);
        }
    }

    /**
     * Clears the pending e-mail change, and removes the row when nothing is left outstanding.
     * <p>
     * Called wherever the account's control is in question: a completed password reset, a password change and an administrator's
     * edit. A request made by a session that was in use during an intrusion must not stay confirmable afterwards.
     *
     * @param userId the account
     */
    public void clearPendingEmailChange(long userId) {
        clear(userId, row -> row.setPendingEmailChange(null));
    }

    /**
     * Clears one specific pending e-mail change, and removes the row when nothing is left outstanding. A request that replaced it in the meantime stays, which is why this
     * is used for the cleanup after a confirmation instead of {@link #clearPendingEmailChange}, whose purpose is to end whatever is pending.
     *
     * @param userId  the account
     * @param request the request to clear, as it was read
     */
    public void clearPendingEmailChangeOfRequest(long userId, PendingEmailChange request) {
        userRecoveryKeyRepository.clearPendingEmailChangeOfRequest(userId, request.getCurrentKeyId(), request.getNewKeyId());
        userRecoveryKeyRepository.deleteIfNothingOutstanding(userId);
    }

    /**
     * Clears the activation key, and removes the row when nothing is left outstanding.
     *
     * @param userId the account
     */
    public void clearActivationKey(long userId) {
        clear(userId, row -> row.setActivationKey(null));
    }

    /**
     * Clears the reset key and its date, and removes the row when nothing is left outstanding.
     *
     * @param userId the account
     */
    public void clearResetKey(long userId) {
        clear(userId, row -> {
            row.setResetKeyId(null);
            row.setResetKeyHash(null);
            row.setResetDate(null);
        });
    }

    /**
     * Removes everything outstanding for the account.
     *
     * @param userId the account
     */
    public void clearAll(long userId) {
        userRecoveryKeyRepository.findByUserId(userId).ifPresent(userRecoveryKeyRepository::delete);
    }

    private void clear(long userId, Consumer<UserRecoveryKey> mutation) {
        userRecoveryKeyRepository.findByUserId(userId).ifPresent(row -> {
            mutation.accept(row);
            if (row.isEmpty()) {
                userRecoveryKeyRepository.delete(row);
            }
            else {
                userRecoveryKeyRepository.save(row);
            }
        });
    }

    /**
     * Saves a row that may have been created concurrently.
     * <p>
     * These rows are keyed on the user id, so two first writes for the same account both find nothing and both insert.
     * The loser gets a primary-key violation, which would surface as an error on a request that did nothing wrong. It
     * reloads instead and applies the change to the row the winner created.
     *
     * @param userId   the account
     * @param mutation the change to apply
     */
    private void saveHandlingConcurrentInsert(long userId, Consumer<UserRecoveryKey> mutation) {
        UserRecoveryKey row = userRecoveryKeyRepository.findByUserId(userId).orElseGet(() -> new UserRecoveryKey(userId));
        mutation.accept(row);
        try {
            userRecoveryKeyRepository.save(row);
        }
        catch (DataIntegrityViolationException concurrentInsert) {
            UserRecoveryKey created = userRecoveryKeyRepository.findByUserId(userId).orElseThrow(() -> concurrentInsert);
            mutation.accept(created);
            userRecoveryKeyRepository.save(created);
        }
    }
}
