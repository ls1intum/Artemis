package de.tum.cit.aet.artemis.globalsearch.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventKind;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventLogEntry;

/**
 * Spring Data JPA repository for {@link IngestionEventLogEntry}.
 * <p>
 * Writes are inserts only. Reads serve the admin activity feed newest-first, and the pruner deletes by age.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Repository
public interface IngestionEventLogRepository extends ArtemisJpaRepository<IngestionEventLogEntry, Long> {

    /**
     * Reads the most recent events, newest first, optionally narrowed to one kind and/or one course.
     * <p>
     * Both filters are applied with a null-means-unfiltered guard so the feed needs a single query rather
     * than one per filter combination.
     *
     * @param kind     the event kind to filter by, or null for every kind
     * @param courseId the course to filter by, or null for every course
     * @param pageable paging and sorting; the caller supplies the page size
     * @return the matching events, newest first
     */
    @Query("""
            SELECT e FROM IngestionEventLogEntry e
            WHERE (:kind IS NULL OR e.kind = :kind)
              AND (:courseId IS NULL OR e.courseId = :courseId)
            ORDER BY e.occurredAt DESC, e.id DESC
            """)
    List<IngestionEventLogEntry> findRecent(@Param("kind") IngestionEventKind kind, @Param("courseId") Long courseId, Pageable pageable);

    /**
     * Counts events of one kind since the given instant, for the feed's rolling summary tiles.
     *
     * @param kind  the event kind to count
     * @param since only events at or after this instant are counted
     * @return the number of matching events
     */
    long countByKindAndOccurredAtGreaterThanEqual(IngestionEventKind kind, ZonedDateTime since);

    /**
     * Deletes events older than the given cutoff. The log is observability data with no reader that makes
     * decisions from it, so old rows are simply dropped rather than archived.
     *
     * @param cutoff events that occurred strictly before this instant are deleted
     * @return the number of deleted rows
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM IngestionEventLogEntry e WHERE e.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") ZonedDateTime cutoff);
}
