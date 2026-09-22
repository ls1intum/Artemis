package de.tum.cit.aet.artemis.globalsearch.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
     * stays truthful (a later reconcile pass must not treat a deleted entity as still synced). A single
     * modifying statement rather than a derived delete: the dispatcher calls this outside any transaction, and a
     * derived delete loads and removes the entity, which needs one.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     */
    @Transactional // ok because of the modifying delete
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SearchableEntitySyncState s WHERE s.entityType = :entityType AND s.entityId = :entityId")
    void deleteByEntityTypeAndEntityId(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    /**
     * Records that a ledger row was checked, without touching its {@code contentHash} or {@code syncedAt}.
     * <p>
     * The drift pass loads a batch of rows, then resolves and hashes each entity before deciding what to write —
     * real work that takes real time, so by the time it writes back, the row it is holding can already be stale.
     * A save of the whole entity would write that stale snapshot's {@code contentHash}/{@code syncedAt} back over
     * whatever the dispatcher wrote for the same row in the meantime, and — since this entity uses
     * {@code GenerationType.IDENTITY} — a save of a row the dispatcher has since deleted would not simply fail:
     * Hibernate cannot tell "existing, concurrently modified" apart from "new" for an identity-generated id it
     * cannot find, so it inserts a fresh row carrying the stale data back into a ledger entry whose entity is gone.
     * <p>
     * Scoping the write to this one column removes both failure modes. It can never overwrite a newer
     * {@code contentHash}/{@code syncedAt}, and because an {@code UPDATE} never inserts, a row already deleted
     * simply matches nothing rather than being recreated.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @param verifiedAt when the drift pass looked at this entity
     */
    @Transactional // ok because of the modifying update
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SearchableEntitySyncState s SET s.verifiedAt = :verifiedAt WHERE s.entityType = :entityType AND s.entityId = :entityId")
    void markVerified(@Param("entityType") String entityType, @Param("entityId") Long entityId, @Param("verifiedAt") ZonedDateTime verifiedAt);

    /**
     * Deletes ledger rows of the given type whose post no longer exists in the database.
     * <p>
     * Post and answer post are excluded from every reconcile pass (see {@code WeaviateReconcileProperties}), so
     * nothing else ever revisits their ledger rows once a bulk delete removes them from the index without an
     * entity id to clean up after itself. Called by the dispatcher when such a bulk delete is confirmed; by then
     * the entity is already gone from the database (see the dispatcher's javadoc for why), so a leaked row cannot
     * be told apart from a legitimate one except by this existence check.
     *
     * @param entityType {@code SearchableEntitySchema.TypeValues.POST}
     */
    @Transactional // ok because of the modifying delete
    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM SearchableEntitySyncState state
            WHERE state.entityType = :entityType
                AND state.entityId NOT IN (SELECT post.id FROM Post post)
            """)
    void deleteStalePostEntries(@Param("entityType") String entityType);

    /**
     * Same as {@link #deleteStalePostEntries}, for answer posts.
     *
     * @param entityType {@code SearchableEntitySchema.TypeValues.ANSWER_POST}
     */
    @Transactional // ok because of the modifying delete
    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM SearchableEntitySyncState state
            WHERE state.entityType = :entityType
                AND state.entityId NOT IN (SELECT answerPost.id FROM AnswerPost answerPost)
            """)
    void deleteStaleAnswerPostEntries(@Param("entityType") String entityType);
}
