package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

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
 * If you don't need deleted user entities, add `WHERE user.isDeleted = FALSE` to your query.
 * </p>
 */
@Profile(PROFILE_LOCALVC)
@Repository
public interface VcsAccessLogRepository extends ArtemisJpaRepository<VcsAccessLog, Long> {

    // @Transactional // ok because of modifying query
    // @Modifying
    // @Query("""
    // UPDATE VcsAccessLog vcsAccessLog
    // SET vcsAccessLog.repositoryActionType = :repositoryActionType
    // WHERE vcsAccessLog.id = (
    // SELECT MAX(log.id)
    // FROM VcsAccessLog log
    // WHERE log.participation.id = :participationId
    // )
    // """)
    // void updateRepositoryActionTypeForNewestLog(@Param("participationId") long participationId, @Param("repositoryActionType") RepositoryActionType repositoryActionType);

    // @Transactional // ok because of modifying query
    // @Modifying
    // @Query("""
    // UPDATE VcsAccessLog vcsAccessLog
    // SET vcsAccessLog.repositoryActionType = :repositoryActionType
    // WHERE vcsAccessLog.id = (
    // SELECT log.id
    // FROM VcsAccessLog log
    // JOIN ProgrammingExerciseStudentParticipation participation ON log.participation.id = participation.id
    // WHERE participation.repositoryUri = :repositoryUri
    // ORDER BY log.id DESC
    // LIMIT 1
    // )
    // """)
    // void updateRepositoryActionTypeForNewestLog(@Param("repositoryUri") String repositoryUri, @Param("repositoryActionType") RepositoryActionType repositoryActionType);
    //
    //
    // @Transactional // ok because of modifying query
    // @Modifying
    // @Query("""
    // UPDATE VcsAccessLog vcsAccessLog
    // SET vcsAccessLog.commitHash = :commitHash
    // WHERE vcsAccessLog.participation.id = :participationId
    // ORDER BY vcsAccessLog.id DESC
    // LIMIT 1
    // """)
    // void updateCommitHashForNewestLog(@Param("participationId") long participationId, @Param("commitHash") String commitHash);

    @Query("""
                SELECT vcsAccessLog
                FROM VcsAccessLog vcsAccessLog
                WHERE vcsAccessLog.participation.id = :participationId
                ORDER BY vcsAccessLog.id DESC
                LIMIT 1
            """)
    Optional<VcsAccessLog> findNewestByParticipationId(@Param("participationId") long participationId);

    @Query("""
                SELECT vcsAccessLog
                FROM VcsAccessLog vcsAccessLog
                LEFT JOIN FETCH ProgrammingExerciseStudentParticipation participation ON vcsAccessLog.participation.id = participation.id
                WHERE participation.repositoryUri = :repositoryUri
                ORDER BY vcsAccessLog.id DESC
                LIMIT 1
            """)
    Optional<VcsAccessLog> findNewestByRepositoryUri(@Param("repositoryUri") String repositoryUri);

    // @Transactional // ok because of modifying query
    // @Modifying
    // @Query("""
    // UPDATE VcsAccessLog vcsAccessLog
    // SET vcsAccessLog.commitHash = :commitHash
    // WHERE vcsAccessLog.id = (
    // SELECT MAX(log.id)
    // FROM VcsAccessLog log
    // LEFT JOIN FETCH ProgrammingExerciseStudentParticipation participation ON log.participation
    // WHERE participation.repositoryUri = :repositoryUri
    // )
    // """)
    // void updateCommitHashForNewestLog(@Param("repositoryUri") String repositoryUri, @Param("commitHash") String commitHash);
    //

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
     * @return a list of ids of the access logs, which have a timestamp before the date
     */
    @Query("""
            SELECT vcsAccessLog.id
            FROM VcsAccessLog vcsAccessLog
            WHERE vcsAccessLog.timestamp < :date
            """)
    List<Long> findAllIdsBeforeDate(@Param("date") ZonedDateTime date);
}
