package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Spring Data JPA repository for the User entity.<br>
 * <br>
 * <p>
 * <b>Note</b>: Please keep in mind that the User entities are soft-deleted when adding new queries to this repository.
 * If you don't need deleted user entities, add `WHERE user.deleted = FALSE` to your query.
 * </p>
 */
@Profile(PROFILE_LOCALVC)
@Lazy
@Repository
public interface VcsAccessLogRepository extends ArtemisJpaRepository<VcsAccessLog, Long> {

    /**
     * Retrieves the newest {@link VcsAccessLog} that one user wrote for one participation, restricted to the given kinds of action.
     * <p>
     * This is how the second half of a git operation finds the entry its first half wrote. The scope is deliberately narrow, because the lookup stands in for an identity that an
     * HTTPS git operation does not carry from one request to the next:
     * <ul>
     * <li>The user and the participation keep the entry of somebody else, or of another repository, from being amended. A build agent entry has no user, so it never matches.</li>
     * <li>The kinds of action keep a push entry from being amended as a clone and the other way round: the fetch half asks for {@code PULL}, the push half for {@code PUSH}.
     * Whatever entry is the newest, an operation cannot change which kind of operation it was.</li>
     * <li>The age keeps an operation whose own entry was never written from amending the entry of an earlier one.</li>
     * </ul>
     *
     * @param userId          the id of the user that performed the operation
     * @param participationId the id of the participation that owns the repository
     * @param actionTypes     the kinds of action the entry may have
     * @param since           the oldest timestamp an entry may have
     * @return the newest matching entry, or empty if there is none
     */
    @Query("""
            SELECT vcsAccessLog
            FROM VcsAccessLog vcsAccessLog
            WHERE vcsAccessLog.user.id = :userId
                AND vcsAccessLog.participation.id = :participationId
                AND vcsAccessLog.repositoryActionType IN :actionTypes
                AND vcsAccessLog.timestamp >= :since
            ORDER BY vcsAccessLog.id DESC
            LIMIT 1
            """)
    Optional<VcsAccessLog> findNewestEntryOfUserForParticipation(@Param("userId") long userId, @Param("participationId") long participationId,
            @Param("actionTypes") Collection<RepositoryActionType> actionTypes, @Param("since") ZonedDateTime since);

    /**
     * Changes the kind of action of an entry, but only if the entry still has the kind the caller expects.
     * <p>
     * This is a claim, not an edit. Operations that run at the same moment can find the same entry, and reading it, changing it and saving it would let each of them believe it
     * has turned it, so that one entry stands for two operations and the other stays what it was. The condition is checked by the database in the same statement that changes
     * the entry, so exactly one of them wins and the other is told so by the number of rows that changed.
     *
     * @param id          the id of the entry
     * @param currentType the kind of action the entry has to have for the change to happen
     * @param newType     the kind of action the entry gets
     * @return 1 if the entry was changed, 0 if it did not exist or did not have the expected kind of action any more
     */
    @Transactional // ok because of update
    @Modifying
    @Query("""
            UPDATE VcsAccessLog vcsAccessLog
            SET vcsAccessLog.repositoryActionType = :newType
            WHERE vcsAccessLog.id = :id
                AND vcsAccessLog.repositoryActionType = :currentType
            """)
    int changeActionTypeIfStill(@Param("id") long id, @Param("currentType") RepositoryActionType currentType, @Param("newType") RepositoryActionType newType);

    /**
     * Retrieves a list of {@link VcsAccessLog} entities associated with the specified participation ID.
     * The results are ordered by the log ID in ascending order.
     *
     * @param participationId the ID of the participation to filter the access logs by.
     * @return a list of {@link VcsAccessLog} entities for the given participation ID, sorted by log ID in ascending order.
     */
    @Query("""
            SELECT vcsAccessLog
            FROM VcsAccessLog vcsAccessLog
            WHERE vcsAccessLog.participation.id = :participationId
            ORDER BY vcsAccessLog.id ASC
            """)
    List<VcsAccessLog> findAllByParticipationId(@Param("participationId") long participationId);

    /**
     * Retrieves a list of {@link VcsAccessLog} entities associated with the specified participation ID.
     * The results are ordered by the log ID in ascending order.
     *
     * @param date The date before which all log ids should be fetched
     * @return a set of ids of the access logs, which have a timestamp before the date
     */
    @Query("""
            SELECT vcsAccessLog.id
            FROM VcsAccessLog vcsAccessLog
            WHERE vcsAccessLog.timestamp < :date
            """)
    Set<Long> findAllIdsBeforeDate(@Param("date") ZonedDateTime date);
}
