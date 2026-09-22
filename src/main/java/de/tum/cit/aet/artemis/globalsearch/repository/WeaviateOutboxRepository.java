package de.tum.cit.aet.artemis.globalsearch.repository;

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
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;

/**
 * Spring Data JPA repository for {@link WeaviateOutboxEntry}.
 * <p>
 * The enqueue side ({@code SearchableEntityWeaviateService}) only calls {@code save}. The dispatcher on the
 * scheduling node reads due rows with {@link #findDueForDispatch(ZonedDateTime, int)} and deletes them once
 * their Weaviate write is confirmed.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Repository
public interface WeaviateOutboxRepository extends ArtemisJpaRepository<WeaviateOutboxEntry, Long> {

    /**
     * Reads due outbox rows for dispatch, oldest enqueue first. This is a plain read with no {@code FOR UPDATE}:
     * the dispatcher runs on the single scheduling node, so there is no concurrent claimer to lock rows against,
     * and the read is not held in a transaction across the Weaviate write. Rows whose backoff has not elapsed
     * ({@code next_attempt_at > now}) are excluded; ordering by {@code id} preserves enqueue order.
     *
     * @param now   the current time; rows with {@code next_attempt_at <= now} are eligible
     * @param limit the maximum number of rows to read in one batch
     * @return the due rows, detached
     */
    @Query(value = """
            SELECT * FROM weaviate_outbox
            WHERE next_attempt_at <= :now
            ORDER BY id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<WeaviateOutboxEntry> findDueForDispatch(@Param("now") ZonedDateTime now, @Param("limit") int limit);

    /**
     * Whether a row is already queued for {@code (entityType, entityId)}. Rows are deleted once their write is
     * confirmed, so any row that exists is still pending.
     * <p>
     * A reconcile pass checks this before enqueueing so it does not pile duplicates onto an entity that is
     * already waiting, which matters most while a large backlog drains slowly. The check is best effort: two
     * passes can race and both enqueue. That is harmless, because the dispatcher re-derives current state at
     * apply time and a duplicate simply writes the same thing twice.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @return {@code true} if a row for this entity is already queued
     */
    boolean existsByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * Deletes outbox rows for {@code (entityType, entityId)} with an id below {@code appliedId}. Called after a
     * per-entity write is confirmed so an older row deferred by backoff cannot retry and overwrite it
     * (latest-wins). {@code flushAutomatically} writes any pending same-entity change before the delete runs.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @param appliedId  the id of the row just applied; rows with a smaller id are removed
     */
    @Transactional // ok because of the modifying delete
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM WeaviateOutboxEntry e WHERE e.entityType = :entityType AND e.entityId = :entityId AND e.id < :appliedId")
    void deleteSupersededByEntity(@Param("entityType") String entityType, @Param("entityId") Long entityId, @Param("appliedId") Long appliedId);

    /**
     * Counts rows already past their backoff, i.e. what the next drain would pick up.
     * <p>
     * Separate from a plain {@code count()}: a queue that is deep but entirely backing off is failing,
     * while a queue that is deep and all due is merely busy, and the admin queue view distinguishes the two.
     *
     * @param now rows with {@code next_attempt_at <= now} are counted
     * @return the number of due rows
     */
    long countByNextAttemptAtLessThanEqual(ZonedDateTime now);

    /**
     * Counts waiting rows grouped by what enqueued them, separating live edits from repair work.
     *
     * @return one row per origin as {@code [origin, count]}
     */
    @Query("SELECT o.origin, COUNT(o) FROM WeaviateOutboxEntry o GROUP BY o.origin")
    List<Object[]> countGroupedByOrigin();

    /**
     * The highest attempt count among waiting rows; above zero means writes are failing and retrying.
     *
     * @return the maximum attempt count, or null when the queue is empty
     */
    @Query("SELECT MAX(o.attempts) FROM WeaviateOutboxEntry o")
    Integer findMaxAttempts();

    /**
     * When the oldest waiting row was enqueued, as the queue's age watermark.
     *
     * @return the earliest enqueue time, or null when the queue is empty
     */
    @Query("SELECT MIN(o.createdAt) FROM WeaviateOutboxEntry o")
    ZonedDateTime findOldestEnqueuedAt();
}
