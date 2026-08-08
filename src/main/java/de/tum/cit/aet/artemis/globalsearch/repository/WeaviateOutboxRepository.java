package de.tum.cit.aet.artemis.globalsearch.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;

/**
 * Spring Data JPA repository for {@link WeaviateOutboxEntry}.
 * <p>
 * The enqueue side ({@code SearchableEntityWeaviateService}) only calls {@code save}. The dispatcher on the
 * scheduling node claims due rows with {@link #claimBatchForDispatch(ZonedDateTime, int)} and deletes them
 * once their Weaviate write is confirmed.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Repository
public interface WeaviateOutboxRepository extends ArtemisJpaRepository<WeaviateOutboxEntry, Long> {

    /**
     * Atomically claim due outbox rows for dispatch, oldest enqueue first.
     * <p>
     * Uses PostgreSQL {@code FOR UPDATE SKIP LOCKED} (same idiom as
     * {@code LectureUnitProcessingStateRepository.findIdleForDispatch}) so a second drain on the same node
     * never processes a row already locked by an in-flight drain. Rows whose backoff has not elapsed
     * ({@code next_attempt_at > now}) are excluded. Ordering by {@code id} preserves enqueue order, which is
     * what makes multiple pending rows for the same entity apply latest-wins when processed sequentially.
     *
     * @param now   the current time; rows with {@code next_attempt_at <= now} are eligible
     * @param limit the maximum number of rows to claim in one batch
     * @return the claimed rows, locked for the duration of the calling transaction
     */
    @Query(value = """
            SELECT * FROM weaviate_outbox
            WHERE next_attempt_at <= :now
            ORDER BY id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WeaviateOutboxEntry> claimBatchForDispatch(@Param("now") ZonedDateTime now, @Param("limit") int limit);
}
