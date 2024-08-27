package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.vcstokens.VcsAccessLog;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the User entity.<br>
 * <br>
 * <p>
 * <b>Note</b>: Please keep in mind that the User entities are soft-deleted when adding new queries to this repository.
 * If you don't need deleted user entities, add `WHERE user.isDeleted = FALSE` to your query.
 * </p>
 */
@Profile(PROFILE_CORE)
@Repository
public interface VcsAccessLogRepository extends ArtemisJpaRepository<VcsAccessLog, Long> {

    /**
     * Find the access log entry which does not have any commit hash yet
     *
     * @param participationId The id of the participation the repository belongs to
     * @return a log entry belonging to the participationId, which has no commit hash
     */
    @Query("""
            SELECT vcsAccessLog
            FROM VcsAccessLog vcsAccessLog
            WHERE vcsAccessLog.participation.id = :participationId
                AND vcsAccessLog.commitHash IS NULL
            """)
    Optional<VcsAccessLog> findByParticipationIdWhereCommitHashIsNull(@Param("participationId") long participationId);

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
    List<VcsAccessLog> findAllByParticipationId(long participationId);

    /**
     * Retrieves a list of {@link VcsAccessLog} entities associated with the specified participation ID.
     * The results are ordered by the log ID in ascending order.
     *
     * @return a list of {@link VcsAccessLog} entities for the given participation ID, sorted by log ID in ascending order.
     */
    @Query("""
            SELECT vcsAccessLog.id
            FROM VcsAccessLog vcsAccessLog
            WHERE vcsAccessLog.timestamp < :date
            """)
    List<Long> findAllIdsBeforeDate(ZonedDateTime date);
}
