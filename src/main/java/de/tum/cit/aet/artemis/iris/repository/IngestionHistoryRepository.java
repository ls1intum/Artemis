package de.tum.cit.aet.artemis.iris.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.IngestionHistoryEntry;

/**
 * Repository for the persisted lecture ingestion history that backs the admin dashboard's recent-history view, so it
 * survives an Artemis restart. Old entries are pruned by a scheduled retention job.
 */
@Conditional(IrisEnabled.class)
@Lazy
@Repository
public interface IngestionHistoryRepository extends ArtemisJpaRepository<IngestionHistoryEntry, Long> {

    /**
     * @param limit the maximum number of entries to return
     * @return the most recent history entries, newest first
     */
    @Query("SELECT entry FROM IngestionHistoryEntry entry ORDER BY entry.finishedAt DESC")
    List<IngestionHistoryEntry> findRecent(org.springframework.data.domain.Pageable limit);

    /**
     * Deletes all history entries that finished before the given cutoff (the retention boundary).
     *
     * @param cutoff entries finished before this time are removed
     * @return the number of deleted entries
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM IngestionHistoryEntry entry WHERE entry.finishedAt < :cutoff")
    int deleteByFinishedAtBefore(@Param("cutoff") ZonedDateTime cutoff);
}
