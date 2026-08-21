package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.UserActivity;
import de.tum.cit.aet.artemis.account.repository.UserActivityRepository;

/**
 * Owns the activity timestamps of an account, which live in {@code user_activity} rather than on the user row.
 * <p>
 * Every existing account was given a row by the migration, so both writes here are normally a single statement that
 * resolves the account by login inside the query. Only an account created afterwards that has never logged in needs its
 * row created first, and only once.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserActivityService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityService.class);

    private final UserActivityRepository userActivityRepository;

    public UserActivityService(UserActivityRepository userActivityRepository) {
        this.userActivityRepository = userActivityRepository;
    }

    /**
     * Records that the account just authenticated.
     *
     * @param login the login of the account
     * @param when  the moment of the login
     */
    public void recordLogin(String login, Instant when) {
        if (userActivityRepository.recordLogin(login, when) == 0) {
            createRowThen(login, row -> row.setLastLoginDate(when));
        }
    }

    /**
     * Records that the account has been warned that it is about to be deleted for inactivity.
     *
     * @param login the login of the account
     * @param when  the moment the warning was sent
     */
    public void recordDeletionWarning(String login, Instant when) {
        if (userActivityRepository.recordDeletionWarning(login, when) == 0) {
            createRowThen(login, row -> row.setDeletionWarningSentDate(when));
        }
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

    /**
     * Creates the row for an account that does not have one yet and applies the given change to it. Reached only by an
     * account created after the migration, on the first write of either timestamp.
     */
    private void createRowThen(String login, java.util.function.Consumer<UserActivity> mutation) {
        userActivityRepository.findUserIdByLogin(login).ifPresentOrElse(userId -> {
            UserActivity row = userActivityRepository.findByUserId(userId).orElseGet(() -> new UserActivity(userId));
            mutation.accept(row);
            userActivityRepository.save(row);
        }, () -> log.debug("No account with login {}, so no activity was recorded", login));
    }
}
