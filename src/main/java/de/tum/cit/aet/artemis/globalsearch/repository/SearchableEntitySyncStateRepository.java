package de.tum.cit.aet.artemis.globalsearch.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;

/**
 * Repository for {@link SearchableEntitySyncState}, mirroring {@code IrisLectureUnitSyncStateRepository}.
 * <p>
 * Because SearchableEntities are polymorphic (no single owning table), the durable-drain helpers lock the
 * sync-state row itself (via {@link #findByEntityTypeAndEntityIdForUpdate}) rather than an owning parent row,
 * and concurrent first inserts are guarded by the unique {@code (entity_type, entity_id)} constraint. The full
 * drain and the write-path switch live in the Ingestion Durable Sync project; here these helpers exist and are
 * unit-tested but drive no production write path.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Repository
public interface SearchableEntitySyncStateRepository extends ArtemisJpaRepository<SearchableEntitySyncState, Long> {

    Optional<SearchableEntitySyncState> findByEntityTypeAndEntityId(String entityType, long entityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT state FROM SearchableEntitySyncState state WHERE state.entityType = :entityType AND state.entityId = :entityId")
    Optional<SearchableEntitySyncState> findByEntityTypeAndEntityIdForUpdate(@Param("entityType") String entityType, @Param("entityId") long entityId);

    List<SearchableEntitySyncState> findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(List<String> statuses, ZonedDateTime now);

    /**
     * Creates or updates the dirty synchronization state for one entity. A row whose content already matches what
     * was last synced (and that holds no active lease) settles back to {@code CLEAN}; otherwise it becomes
     * {@code DIRTY} with the given retry time, unless an in-progress lease is still active, which is preserved.
     *
     * @param entityType  the entity type discriminator
     * @param entityId    the entity id
     * @param contentHash the new desired content hash, or null to leave it unchanged
     * @param nextRetryAt the earliest retry time for a dirty row
     * @return the persisted state
     */
    @Transactional
    default SearchableEntitySyncState markDirty(String entityType, long entityId, String contentHash, ZonedDateTime nextRetryAt) {
        SearchableEntitySyncState state = findByEntityTypeAndEntityId(entityType, entityId).orElseGet(() -> {
            SearchableEntitySyncState created = new SearchableEntitySyncState();
            created.setEntityType(entityType);
            created.setEntityId(entityId);
            return created;
        });
        if (contentHash != null) {
            state.setContentHash(contentHash);
        }
        boolean hasActiveLease = SearchableEntitySyncState.STATUS_IN_PROGRESS.equals(state.getStatus()) && state.getNextRetryAt() != null
                && state.getNextRetryAt().isAfter(nextRetryAt);
        if (!hasActiveLease && Objects.equals(state.getContentHash(), state.getLastSyncedContentHash())) {
            state.setStatus(SearchableEntitySyncState.STATUS_CLEAN);
            state.setNextRetryAt(null);
            return save(state);
        }
        if (hasActiveLease) {
            state.setStatus(SearchableEntitySyncState.STATUS_IN_PROGRESS);
        }
        else {
            state.setStatus(SearchableEntitySyncState.STATUS_DIRTY);
            state.setNextRetryAt(nextRetryAt);
        }
        return save(state);
    }

    /**
     * Claims a due retry by moving its next retry time to a lease deadline while holding a write lock on the
     * sync-state row, so a second worker cannot claim the same row concurrently.
     *
     * @param entityType the entity type discriminator
     * @param entityId   the entity id
     * @param now        the current time
     * @param leaseUntil the deadline after which another worker may reclaim an unfinished retry
     * @return the claimed state, or empty if the row is absent, not due, or already terminal
     */
    @Transactional
    default Optional<SearchableEntitySyncState> claimRetry(String entityType, long entityId, ZonedDateTime now, ZonedDateTime leaseUntil) {
        return findByEntityTypeAndEntityIdForUpdate(entityType, entityId)
                .filter(state -> SearchableEntitySyncState.STATUS_DIRTY.equals(state.getStatus()) || SearchableEntitySyncState.STATUS_IN_PROGRESS.equals(state.getStatus()))
                .filter(state -> state.getNextRetryAt() != null && !state.getNextRetryAt().isAfter(now)).map(state -> {
                    state.setStatus(SearchableEntitySyncState.STATUS_IN_PROGRESS);
                    state.setNextRetryAt(leaseUntil);
                    return saveAndFlush(state);
                });
    }
}
