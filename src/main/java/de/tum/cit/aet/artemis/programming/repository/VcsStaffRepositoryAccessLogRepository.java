package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsStaffRepositoryAccessLog;

/**
 * Spring Data JPA repository for the access log of the tests and auxiliary repositories of programming exercises, see {@link VcsStaffRepositoryAccessLog}.
 */
@Profile(PROFILE_LOCALVC)
@Lazy
@Repository
public interface VcsStaffRepositoryAccessLogRepository extends ArtemisJpaRepository<VcsStaffRepositoryAccessLog, Long> {

    /**
     * The entries of a repository of an exercise that has no participation, oldest first. The user is fetched with them, because the log is read after the query.
     *
     * @param exerciseId     the exercise the repository belongs to
     * @param repositoryType the tests or the auxiliary repositories
     * @return the entries, ordered by id
     */
    @Query("""
            SELECT entry
            FROM VcsStaffRepositoryAccessLog entry
                LEFT JOIN FETCH entry.user
            WHERE entry.exercise.id = :exerciseId
                AND entry.repositoryType = :repositoryType
            ORDER BY entry.id ASC
            """)
    List<VcsStaffRepositoryAccessLog> findAllByExerciseIdAndRepositoryType(@Param("exerciseId") long exerciseId, @Param("repositoryType") RepositoryType repositoryType);

    /**
     * The entries of an auxiliary repository, oldest first. The user is fetched with them, because the log is read after the query.
     *
     * @param auxiliaryRepositoryId the auxiliary repository
     * @return the entries, ordered by id
     */
    @Query("""
            SELECT entry
            FROM VcsStaffRepositoryAccessLog entry
                LEFT JOIN FETCH entry.user
            WHERE entry.auxiliaryRepository.id = :auxiliaryRepositoryId
            ORDER BY entry.id ASC
            """)
    List<VcsStaffRepositoryAccessLog> findAllByAuxiliaryRepositoryId(@Param("auxiliaryRepositoryId") long auxiliaryRepositoryId);

    /**
     * The ids of the entries older than a date, for the cleanup.
     *
     * @param date the date before which the entries are outdated
     * @return the ids of the entries with an earlier timestamp
     */
    @Query("""
            SELECT entry.id
            FROM VcsStaffRepositoryAccessLog entry
            WHERE entry.timestamp < :date
            """)
    Set<Long> findAllIdsBeforeDate(@Param("date") ZonedDateTime date);
}
