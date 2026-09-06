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

import de.tum.cit.aet.artemis.account.domain.UserRecoveryKey;
import de.tum.cit.aet.artemis.account.repository.UserRecoveryKeyRepository;

/**
 * Owns the activation and password-reset keys of an account, which live in {@code user_recovery_key} rather than on the
 * user row.
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
     * The reset key the account currently has outstanding, or null when it has none.
     *
     * @param userId the account
     * @return the reset key, or null
     */
    @Nullable
    public String findResetKey(long userId) {
        return userRecoveryKeyRepository.findByUserId(userId).map(UserRecoveryKey::getResetKey).orElse(null);
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
     * @param userId    the account
     * @param resetKey  the key to store
     * @param resetDate when the key was issued, which is what bounds its validity
     */
    public void storeResetKey(long userId, String resetKey, Instant resetDate) {
        saveHandlingConcurrentInsert(userId, row -> {
            row.setResetKey(resetKey);
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
     * Finds the account whose password reset the given key completes, together with when the key was issued so the
     * caller can reject a stale one.
     *
     * @param resetKey the key presented by the caller
     * @return the pending row, or empty if no account has that key outstanding
     */
    public Optional<UserRecoveryKey> findByResetKey(@Nullable String resetKey) {
        if (!StringUtils.hasText(resetKey)) {
            // See findUserIdByActivationKey: a null key must not match the row of an account that has none outstanding.
            return Optional.empty();
        }
        return userRecoveryKeyRepository.findByResetKey(resetKey);
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
            row.setResetKey(null);
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
