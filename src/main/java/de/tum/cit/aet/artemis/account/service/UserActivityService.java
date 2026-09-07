package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.UserActivity;
import de.tum.cit.aet.artemis.account.repository.UserActivityRepository;

/**
 * Owns the activity timestamps of an account, which live in {@code user_activity} rather than on the user row.
 * <p>
 * Both writes here are normally a single statement that resolves the account by login inside the query. Only an account
 * that has no row yet needs one created first, which happens at most once per account.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserActivityService {

    private final UserActivityRepository userActivityRepository;

    public UserActivityService(UserActivityRepository userActivityRepository) {
        this.userActivityRepository = userActivityRepository;
    }

    /**
     * Records that the account just authenticated.
     * <p>
     * The production login path does not come through here: it is triggered from {@code CustomAuditEventRepository}, and a
     * repository must not reach into a service, so it calls the same repository method directly.
     *
     * @param login the login of the account
     * @param when  the moment of the login
     */
    public void recordLogin(String login, Instant when) {
        userActivityRepository.recordLoginCreatingRowIfMissing(login, when);
    }

    /**
     * Records that the account has been warned that it is about to be deleted for inactivity.
     *
     * @param login the login of the account
     * @param when  the moment the warning was sent
     */
    public void recordDeletionWarning(String login, Instant when) {
        userActivityRepository.recordDeletionWarningCreatingRowIfMissing(login, when);
    }

    /**
     * Records that the account's credentials changed, so that sessions established before this moment are no longer
     * extended.
     *
     * @param userId the account
     * @param when   the moment the credentials changed
     */
    public void recordCredentialsChanged(long userId, Instant when) {
        userActivityRepository.recordCredentialsChangedCreatingRowIfMissing(userId, when);
    }

    /**
     * When the account's credentials last changed, or null if they never have.
     *
     * @param userId the account
     * @return the timestamp, or null
     */
    @Nullable
    public Instant findCredentialsChangedDate(long userId) {
        return userActivityRepository.findByUserId(userId).map(UserActivity::getCredentialsChangedDate).orElse(null);
    }

    /**
     * Clears the deletion warning of accounts that came back, either by enrolling in a course again or by logging in
     * after being warned.
     *
     * @return the number of accounts whose warning was cleared
     */
    public int clearDeletionWarningForReturnedUsers() {
        return userActivityRepository.clearDeletionWarningForReturnedUsers();
    }

    /**
     * The moment the account last authenticated, or null if it never has.
     *
     * @param userId the account
     * @return the last login, or null
     */
    public Instant findLastLoginDate(long userId) {
        return userActivityRepository.findByUserId(userId).map(UserActivity::getLastLoginDate).orElse(null);
    }

    /**
     * The moment the account was warned about deletion, or null if it has not been warned.
     *
     * @param userId the account
     * @return the warning timestamp, or null
     */
    public Instant findDeletionWarningSentDate(long userId) {
        return userActivityRepository.findByUserId(userId).map(UserActivity::getDeletionWarningSentDate).orElse(null);
    }

}
