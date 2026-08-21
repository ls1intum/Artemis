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

import de.tum.cit.aet.artemis.account.domain.UserActivity;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface UserActivityRepository extends ArtemisJpaRepository<UserActivity, Long> {

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
