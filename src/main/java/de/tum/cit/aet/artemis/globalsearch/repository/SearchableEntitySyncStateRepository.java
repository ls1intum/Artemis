package de.tum.cit.aet.artemis.globalsearch.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;

/**
 * Spring Data JPA repository for {@link SearchableEntitySyncState}, the ledger of confirmed Weaviate writes.
 * <p>
 * The dispatcher upserts a row here on every successful metadata write. A later reconcile pass reads it to
 * detect never-indexed or drifted entities.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Repository
public interface SearchableEntitySyncStateRepository extends ArtemisJpaRepository<SearchableEntitySyncState, Long> {

    /**
     * Finds the ledger row for a single entity.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @return the ledger row if one exists
     */
    Optional<SearchableEntitySyncState> findByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * Returns which of the given ids already have a confirmed write on record, so a pass can diff a page of
     * database ids against the ledger in one query rather than asking per entity.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityIds  the ids to look up
     * @return the subset of ids that are present in the ledger
     */
    /**
     * Counts the ledger rows per entity type. The ledger is what the missing and drift passes reason about, so its
     * size per type is the difference between "the reconciler has nothing to do" and "the reconciler cannot see
     * that there is anything to do".
     *
     * @return one row per entity type that has at least one ledger entry, as {@code [entityType, count]}
     */
    @Query("""
            SELECT state.entityType, COUNT(state)
            FROM SearchableEntitySyncState state
            GROUP BY state.entityType
            ORDER BY state.entityType
            """)
    List<Object[]> countByEntityType();

    @Query("""
            SELECT state.entityId
            FROM SearchableEntitySyncState state
            WHERE state.entityType = :entityType
                AND state.entityId IN :entityIds
            """)
    Set<Long> findSyncedEntityIds(@Param("entityType") String entityType, @Param("entityIds") Collection<Long> entityIds);

    /**
     * Reads the ledger rows for a set of ids of one type, so a pass holding a page of index rows can compare them
     * against what was last written in a single query.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityIds  the ids to look up
     * @return the ledger rows that exist for those ids
     */
    List<SearchableEntitySyncState> findAllByEntityTypeAndEntityIdIn(String entityType, Collection<Long> entityIds);

    /**
     * Reads the ledger rows that have gone longest without being checked against the database, restricted to the
     * types the reconcile passes manage.
     * <p>
     * Ordering this way is what makes the drift pass a rolling sweep: checking a row is what moves it to the back
     * of the queue, so the pass needs no stored position and every entity comes round again on a fixed cycle.
     *
     * @param entityTypes the types the passes manage
     * @param pageable    the slice size
     * @return the least recently verified rows first
     */
    @Query("""
            SELECT state
            FROM SearchableEntitySyncState state
            WHERE state.entityType IN :entityTypes
            ORDER BY state.verifiedAt ASC
            """)
    List<SearchableEntitySyncState> findLeastRecentlyVerified(@Param("entityTypes") Collection<String> entityTypes, Pageable pageable);

    /**
     * Deletes the ledger row for a single entity, if present. Called when an entity is deleted so the ledger
     * stays truthful (a later reconcile pass must not treat a deleted entity as still synced).
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     */
    void deleteByEntityTypeAndEntityId(String entityType, Long entityId);
}
