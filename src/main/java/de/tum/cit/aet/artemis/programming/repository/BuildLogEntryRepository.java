package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

/**
 * Spring Data JPA repository for the BuildLogEntry entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface BuildLogEntryRepository extends ArtemisJpaRepository<BuildLogEntry, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteByProgrammingSubmissionId(long programmingSubmissionId);

    /**
     * Finds the ids of build log entries older than the given cutoff, oldest first and a page at a time. The ordering matches the {@code (time, id)} retention index, so every
     * batch can be selected without scanning and sorting the legacy table again.
     * <p>
     * Nothing writes to this table any more; the logs of a failed build are stored on disk. This is what drains what is left of it, so that the rows disappear on the same
     * retention period as the files rather than staying until someone drops the table.
     *
     * @param cutoff   entries produced before this point are expired
     * @param pageable how many ids to return in one batch
     * @return the ids of expired entries, at most one page of them
     */
    /**
     * Deletes the given build log entries in one statement.
     * <p>
     * {@code deleteAllById} inherited from Spring Data loads and removes one entity at a time, which over a full cleanup run is a million statements rather than two
     * hundred.
     *
     * @param ids the ids to delete
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM BuildLogEntry buildLogEntry
            WHERE buildLogEntry.id IN :ids
            """)
    void deleteAllByIdIn(@Param("ids") List<Long> ids);

    @Query("""
            SELECT buildLogEntry.id
            FROM BuildLogEntry buildLogEntry
            WHERE buildLogEntry.time < :cutoff
            ORDER BY buildLogEntry.time, buildLogEntry.id
            """)
    List<Long> findExpiredIds(@Param("cutoff") ZonedDateTime cutoff, Pageable pageable);

}
