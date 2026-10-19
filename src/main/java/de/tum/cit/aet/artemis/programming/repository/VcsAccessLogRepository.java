package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;

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
     * Retrieves the most recent {@link VcsAccessLog} for a given participation ID.
     *
     * @param participationId the ID of the participation to filter by.
     *                            <p>
     *                            Build agent entries are excluded, and the exclusion is the point rather than a detail: an agent clone of the
     *                            same repository is written to this table with no user, so without it a clone that lands between a person's push
     *                            and this lookup would take the amendment meant for that person - putting their commit hash on the agent's row
     *                            and leaving their own without one.
     *
     * @return an {@link Optional} containing the newest {@link VcsAccessLog} written for a person, or empty if none
     *         exists.
     */
    @Query("""
            SELECT vcsAccessLog
            FROM VcsAccessLog vcsAccessLog
            WHERE vcsAccessLog.participation.id = :participationId
                AND vcsAccessLog.user IS NOT NULL
            ORDER BY vcsAccessLog.id DESC
            LIMIT 1
            """)
    Optional<VcsAccessLog> findNewestUserEntryByParticipationId(@Param("participationId") long participationId);

    /**
     * Retrieves the most recent {@link VcsAccessLog} for a specific repository URI of a participation.
     *
     * @param repositoryUri the URI of the participation to filter by.
     *                          <p>
     *                          Build agent entries are excluded, for the same reason as above: they belong to no person and must not absorb the
     *                          clone-or-pull label of somebody else's git operation.
     *
     * @return an Optional containing the newest {@link VcsAccessLog} written for a person, or empty if none exists.
     */
    @Query("""
            SELECT vcsAccessLog
            FROM VcsAccessLog vcsAccessLog
                LEFT JOIN TREAT (vcsAccessLog.participation AS ProgrammingExerciseStudentParticipation) participation
            WHERE participation.repositoryUri = :repositoryUri
                AND vcsAccessLog.user IS NOT NULL
            ORDER BY vcsAccessLog.id DESC
            LIMIT 1
            """)
    Optional<VcsAccessLog> findNewestUserEntryByRepositoryUri(@Param("repositoryUri") String repositoryUri);

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
