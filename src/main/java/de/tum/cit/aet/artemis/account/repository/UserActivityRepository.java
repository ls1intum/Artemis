package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.UserActivity;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserActivityRepository extends ArtemisJpaRepository<UserActivity, Long> {

    Logger log = LoggerFactory.getLogger(UserActivityRepository.class);

    Optional<UserActivity> findByUserId(long userId);

    /**
     * Records a login, resolving the account by login inside the same statement.
     * <p>
     * Deliberately a subquery rather than a lookup followed by an update: this runs on every successful authentication,
     * and the point of the separate table is to make that write touch three columns instead of the whole user row. Paying
     * for an extra round trip to find the id would cancel that out.
     *
     * @param login         the login of the account that just authenticated
     * @param lastLoginDate the moment of the login
     * @return the number of updated rows, 0 if the account has no row yet
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserActivity activity
            SET activity.lastLoginDate = :lastLoginDate
            WHERE activity.userId = (SELECT user.id FROM User user WHERE user.login = :login)
            """)
    int recordLogin(@Param("login") String login, @Param("lastLoginDate") Instant lastLoginDate);

    /**
     * Records that the account has been warned about an upcoming deletion.
     *
     * @param login the login of the warned account
     * @param date  the warning timestamp
     * @return the number of updated rows, 0 if the account has no row yet
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserActivity activity
            SET activity.deletionWarningSentDate = :date
            WHERE activity.userId = (SELECT user.id FROM User user WHERE user.login = :login)
            """)
    int recordDeletionWarning(@Param("login") String login, @Param("date") Instant date);

    /**
     * Records that the account's credentials changed, keyed on the account id since every caller already holds it.
     *
     * @param userId the account
     * @param when   the moment the credentials changed
     * @return the number of updated rows, 0 if the account has no row yet
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserActivity activity
            SET activity.credentialsChangedDate = :when
            WHERE activity.userId = :userId
            """)
    int recordCredentialsChanged(@Param("userId") long userId, @Param("when") Instant when);

    /**
     * Records that the account's credentials changed, creating the row first if it does not have one.
     *
     * @param userId the account
     * @param when   the moment the credentials changed
     */
    default void recordCredentialsChangedCreatingRowIfMissing(long userId, Instant when) {
        if (recordCredentialsChanged(userId, when) == 0) {
            applyCreatingRowIfMissing(userId, row -> row.setCredentialsChangedDate(when), () -> recordCredentialsChanged(userId, when));
        }
    }

    /**
     * Clears the deletion warning of accounts that came back: enrolled in a course again, or logged in after being
     * warned.
     *
     * @return the number of accounts whose warning was cleared
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE UserActivity activity
            SET activity.deletionWarningSentDate = NULL
            WHERE activity.deletionWarningSentDate IS NOT NULL
                AND (EXISTS (SELECT ucr FROM UserCourseRole ucr WHERE ucr.user.id = activity.userId)
                    OR (activity.lastLoginDate IS NOT NULL AND activity.lastLoginDate >= activity.deletionWarningSentDate))
            """)
    int clearDeletionWarningForReturnedUsers();

    /**
     * Records a login, creating the row first if the account does not have one yet.
     * <p>
     * Lives here rather than in {@code UserActivityService} because the login is recorded from
     * {@code CustomAuditEventRepository}, and a repository must not reach into a service.
     *
     * @param login the login of the account that just authenticated
     * @param when  the moment of the login
     */
    default void recordLoginCreatingRowIfMissing(String login, Instant when) {
        if (recordLogin(login, when) == 0) {
            applyCreatingRowIfMissing(login, row -> row.setLastLoginDate(when), () -> recordLogin(login, when));
        }
    }

    /**
     * Records a deletion warning, creating the row first if the account does not have one yet.
     *
     * @param login the login of the warned account
     * @param when  the moment the warning was sent
     */
    default void recordDeletionWarningCreatingRowIfMissing(String login, Instant when) {
        if (recordDeletionWarning(login, when) == 0) {
            applyCreatingRowIfMissing(login, row -> row.setDeletionWarningSentDate(when), () -> recordDeletionWarning(login, when));
        }
    }

    /**
     * Applies a change to the activity row of the account with the given login, creating the row if it has none.
     *
     * @param login    the login of the account whose row is written
     * @param mutation the change to apply to the row
     * @param update   the single-statement update to retry if another writer creates the row first
     */
    private void applyCreatingRowIfMissing(String login, Consumer<UserActivity> mutation, IntSupplier update) {
        findUserIdByLogin(login).ifPresentOrElse(userId -> applyCreatingRowIfMissing(userId, mutation, update),
                () -> log.debug("No account with login {}, so no activity was recorded", login));
    }

    /**
     * Applies a change to the account's activity row, creating the row if the account has none.
     * <p>
     * Reached only while an account has no row, so in practice only on the first write of one of its timestamps. Two such
     * writes at the same moment both find no row and both try to insert one, so the loser gets a primary-key violation.
     * It retries the single-statement update, which the winner has just made possible, rather than losing the timestamp
     * to a race. A violation that leaves the update still matching nothing is not this race and reaches the caller.
     *
     * @param userId   the account whose row is written
     * @param mutation the change to apply to the row
     * @param update   the single-statement update to retry if another writer creates the row first
     */
    private void applyCreatingRowIfMissing(long userId, Consumer<UserActivity> mutation, IntSupplier update) {
        UserActivity row = findByUserId(userId).orElseGet(() -> new UserActivity(userId));
        mutation.accept(row);
        try {
            save(row);
        }
        catch (DataIntegrityViolationException concurrentInsert) {
            if (update.getAsInt() == 0) {
                throw concurrentInsert;
            }
        }
    }

    /**
     * Resolves the id of the account with the given login, for the rare case where a login has to create the row first.
     *
     * @param login the login
     * @return the account id, or empty if no account has that login
     */
    @Query("""
            SELECT user.id
            FROM User user
            WHERE user.login = :login
            """)
    Optional<Long> findUserIdByLogin(@Param("login") String login);
}
