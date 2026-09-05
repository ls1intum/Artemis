package de.tum.cit.aet.artemis.account.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.ConductAgreement;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Removes the rows the account module owns for a user that is being deleted permanently.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 *
 * <p>
 * Every method names its entity and field, so a renamed table or column stops the build rather than the deletion. The
 * counting methods take every account being previewed at once and group by account, so a preview costs one query per
 * reference however many accounts it covers.
 *
 * <p>
 * The two membership tables are addressed natively because they are join tables of {@code User} and have no entity of
 * their own; there is nothing for JPQL to name.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AccountDataCleanupRepository extends ArtemisJpaRepository<ConductAgreement, Long> {

    @Query("""
            SELECT agreement.user.id AS userId, COUNT(agreement) AS count
            FROM ConductAgreement agreement
            WHERE agreement.user.id IN :userIds
            GROUP BY agreement.user.id
            """)
    List<UserReferenceCount> countConductAgreements(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ConductAgreement agreement
            WHERE agreement.user.id = :userId
            """)
    int deleteConductAgreements(@Param("userId") long userId);

    @Query("""
            SELECT activity.userId AS userId, COUNT(activity) AS count
            FROM UserActivity activity
            WHERE activity.userId IN :userIds
            GROUP BY activity.userId
            """)
    List<UserReferenceCount> countActivities(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserActivity activity
            WHERE activity.userId = :userId
            """)
    int deleteActivities(@Param("userId") long userId);

    @Query("""
            SELECT preference.userId AS userId, COUNT(preference) AS count
            FROM UserAiPreference preference
            WHERE preference.userId IN :userIds
            GROUP BY preference.userId
            """)
    List<UserReferenceCount> countAiPreferences(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserAiPreference preference
            WHERE preference.userId = :userId
            """)
    int deleteAiPreferences(@Param("userId") long userId);

    @Query("""
            SELECT recoveryKey.userId AS userId, COUNT(recoveryKey) AS count
            FROM UserRecoveryKey recoveryKey
            WHERE recoveryKey.userId IN :userIds
            GROUP BY recoveryKey.userId
            """)
    List<UserReferenceCount> countRecoveryKeys(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserRecoveryKey recoveryKey
            WHERE recoveryKey.userId = :userId
            """)
    int deleteRecoveryKeys(@Param("userId") long userId);

    @Query("""
            SELECT credential.user.id AS userId, COUNT(credential) AS count
            FROM PasskeyCredential credential
            WHERE credential.user.id IN :userIds
            GROUP BY credential.user.id
            """)
    List<UserReferenceCount> countPasskeyCredentials(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM PasskeyCredential credential
            WHERE credential.user.id = :userId
            """)
    int deletePasskeyCredentials(@Param("userId") long userId);

    @Query(nativeQuery = true, value = """
            SELECT user_id AS userId, COUNT(*) AS count
            FROM jhi_user_authority
            WHERE user_id IN :userIds
            GROUP BY user_id
            """)
    List<UserReferenceCount> countAuthorities(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query(nativeQuery = true, value = """
            DELETE FROM jhi_user_authority
            WHERE user_id = :userId
            """)
    int deleteAuthorities(@Param("userId") long userId);

    @Query(nativeQuery = true, value = """
            SELECT user_id AS userId, COUNT(*) AS count
            FROM user_organization
            WHERE user_id IN :userIds
            GROUP BY user_id
            """)
    List<UserReferenceCount> countOrganizationMemberships(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query(nativeQuery = true, value = """
            DELETE FROM user_organization
            WHERE user_id = :userId
            """)
    int deleteOrganizationMemberships(@Param("userId") long userId);
}
