package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.UserVCSAccessToken;
import de.tum.cit.aet.artemis.programming.repository.UserVCSAccessTokenRepository;

/**
 * Owns the personal VCS access token of a user, which lives in {@code user_vcs_access_token} rather than on the user
 * row.
 * <p>
 * Callers go through this service rather than the repository so that "the account has no token" is resolved in one
 * place. A row exists only for an account that has a token, and handing callers a bare {@code Optional} would make a
 * forgotten {@code orElse} look like a working code path.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserVcsAccessTokenService {

    private final UserVCSAccessTokenRepository userVcsAccessTokenRepository;

    public UserVcsAccessTokenService(UserVCSAccessTokenRepository userVcsAccessTokenRepository) {
        this.userVcsAccessTokenRepository = userVcsAccessTokenRepository;
    }

    /**
     * The token of the given account, or null when it has none.
     *
     * @param userId the account
     * @return the token value, or null
     */
    @Nullable
    public String findToken(long userId) {
        return userVcsAccessTokenRepository.findByUserId(userId).map(UserVCSAccessToken::getToken).orElse(null);
    }

    /**
     * The expiry date of the account's token, or null when it has none.
     *
     * @param userId the account
     * @return the expiry date, or null
     */
    @Nullable
    public ZonedDateTime findExpiryDate(long userId) {
        return userVcsAccessTokenRepository.findByUserId(userId).map(UserVCSAccessToken::getExpiryDate).orElse(null);
    }

    /**
     * Whether the account has a token that may still be used to authenticate, which an account without a row never has.
     *
     * @param userId the account
     * @return true if a usable token exists
     */
    public boolean hasUsableToken(long userId) {
        return userVcsAccessTokenRepository.findByUserId(userId).filter(UserVCSAccessToken::isUsable).isPresent();
    }

    /**
     * The stored token of the account, if it has one that may still be used. Returns empty both when there is no row and
     * when the token has expired, so a caller comparing a presented secret cannot accidentally accept an expired one.
     *
     * @param userId the account
     * @return the usable token, or empty
     */
    public Optional<UserVCSAccessToken> findUsableToken(long userId) {
        return userVcsAccessTokenRepository.findByUserId(userId).filter(UserVCSAccessToken::isUsable);
    }

    /**
     * Stores the account's token, replacing any previous one.
     *
     * @param userId     the account
     * @param token      the token value
     * @param expiryDate when the token stops being usable
     */
    public void store(long userId, String token, ZonedDateTime expiryDate) {
        saveHandlingConcurrentInsert(userId, stored -> {
            stored.setToken(token);
            stored.setExpiryDate(expiryDate);
        });
    }

    /**
     * Removes the account's token. Deleting the row rather than nulling the columns keeps "no row" the single
     * representation of "no token".
     *
     * @param userId the account
     */
    public void revoke(long userId) {
        userVcsAccessTokenRepository.deleteByUserId(userId);
    }

    /**
     * The accounts whose token expires within the given window, for the notification that warns their owners.
     *
     * @param from start of the window
     * @param to   end of the window
     * @return the affected user ids
     */
    public List<Long> findUserIdsWithTokenExpiringBetween(ZonedDateTime from, ZonedDateTime to) {
        return userVcsAccessTokenRepository.findUserIdsWithTokenExpiringBetween(from, to);
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
    private void saveHandlingConcurrentInsert(long userId, Consumer<UserVCSAccessToken> mutation) {
        UserVCSAccessToken row = userVcsAccessTokenRepository.findByUserId(userId).orElseGet(() -> new UserVCSAccessToken(userId, null, null));
        mutation.accept(row);
        try {
            userVcsAccessTokenRepository.save(row);
        }
        catch (DataIntegrityViolationException concurrentInsert) {
            UserVCSAccessToken created = userVcsAccessTokenRepository.findByUserId(userId).orElseThrow(() -> concurrentInsert);
            mutation.accept(created);
            userVcsAccessTokenRepository.save(created);
        }
    }
}
