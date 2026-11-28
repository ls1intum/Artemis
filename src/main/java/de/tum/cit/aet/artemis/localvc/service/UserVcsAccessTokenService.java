package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.UserVCSAccessToken;
import de.tum.cit.aet.artemis.programming.repository.UserVCSAccessTokenRepository;

/**
 * Owns the personal VCS access token of a user, which lives in {@code user_vcs_access_token} rather than on the user
 * row.
 * <p>
 * The personal token is deprecated: it opens every repository of its owner, where the access tokens of a single participation
 * or repository open one. No new token can be created. Tokens that exist keep authenticating until they expire, and their
 * owner can revoke them, which is what remains of the feature for a token that leaked.
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
     * Removes the account's token. Deleting the row rather than nulling the columns keeps "no row" the single
     * representation of "no token".
     *
     * @param userId the account
     */
    public void revoke(long userId) {
        userVcsAccessTokenRepository.deleteByUserId(userId);
    }

    /**
     * The accounts whose token expires within the given window, for the notification that tells their owners that it has expired.
     *
     * @param from start of the window
     * @param to   end of the window
     * @return the affected user ids
     */
    public List<Long> findUserIdsWithTokenExpiringBetween(ZonedDateTime from, ZonedDateTime to) {
        return userVcsAccessTokenRepository.findUserIdsWithTokenExpiringBetween(from, to);
    }
}
