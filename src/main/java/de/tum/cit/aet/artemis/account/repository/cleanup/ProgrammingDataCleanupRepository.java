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

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.UserVCSAccessToken;

/**
 * Removes the repository credentials and editor settings of a user that is being deleted permanently.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ProgrammingDataCleanupRepository extends ArtemisJpaRepository<UserVCSAccessToken, Long> {

    @Query("""
            SELECT token.userId AS userId, COUNT(token) AS count
            FROM UserVCSAccessToken token
            WHERE token.userId IN :userIds
            GROUP BY token.userId
            """)
    List<UserReferenceCount> countPersonalAccessTokens(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserVCSAccessToken token
            WHERE token.userId = :userId
            """)
    int deletePersonalAccessTokens(@Param("userId") long userId);

    /**
     * The access log records what happened to a repository, which stays true once the person who acted is gone, so
     * the entries are detached rather than deleted.
     *
     * @param userId the account being deleted
     * @return how many entries were detached
     */
    @Modifying
    @Transactional // ok because of update
    @Query("""
            UPDATE VcsAccessLog log
            SET log.user = NULL
            WHERE log.user.id = :userId
            """)
    int detachVcsAccessLogs(@Param("userId") long userId);

    @Query("""
            SELECT log.user.id AS userId, COUNT(log) AS count
            FROM VcsAccessLog log
            WHERE log.user.id IN :userIds
            GROUP BY log.user.id
            """)
    List<UserReferenceCount> countVcsAccessLogs(@Param("userIds") Collection<Long> userIds);

    @Query("""
            SELECT token.user.id AS userId, COUNT(token) AS count
            FROM ParticipationVCSAccessToken token
            WHERE token.user.id IN :userIds
            GROUP BY token.user.id
            """)
    List<UserReferenceCount> countParticipationAccessTokens(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM ParticipationVCSAccessToken token
            WHERE token.user.id = :userId
            """)
    int deleteParticipationAccessTokens(@Param("userId") long userId);

    @Query("""
            SELECT token.user.id AS userId, COUNT(token) AS count
            FROM RepositoryVCSAccessToken token
            WHERE token.user.id IN :userIds
            GROUP BY token.user.id
            """)
    List<UserReferenceCount> countRepositoryAccessTokens(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM RepositoryVCSAccessToken token
            WHERE token.user.id = :userId
            """)
    int deleteRepositoryAccessTokens(@Param("userId") long userId);

    @Query("""
            SELECT key.userId AS userId, COUNT(key) AS count
            FROM UserSshPublicKey key
            WHERE key.userId IN :userIds
            GROUP BY key.userId
            """)
    List<UserReferenceCount> countSshPublicKeys(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserSshPublicKey key
            WHERE key.userId = :userId
            """)
    int deleteSshPublicKeys(@Param("userId") long userId);

    @Query("""
            SELECT mapping.user.id AS userId, COUNT(mapping) AS count
            FROM UserIdeMapping mapping
            WHERE mapping.user.id IN :userIds
            GROUP BY mapping.user.id
            """)
    List<UserReferenceCount> countIdeMappings(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM UserIdeMapping mapping
            WHERE mapping.user.id = :userId
            """)
    int deleteIdeMappings(@Param("userId") long userId);
}
