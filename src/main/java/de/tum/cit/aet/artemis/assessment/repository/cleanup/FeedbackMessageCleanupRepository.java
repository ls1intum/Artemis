package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackMessage;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for garbage-collecting unreferenced {@link FeedbackMessage} rows.
 * <p>
 * The referencing columns carry no database foreign key (by design, to avoid an index over tens of
 * millions of rows), and feedback rows can be deleted by a database-level cascade without the application
 * running — so unreferenced messages must be collected by this scan, which runs as part of the admin
 * orphan cleanup (deliberately not scheduled: it is the one cleanup query that reads the whole of both
 * referencing tables).
 * <p>
 * Both databases plan the anti-join as a single linear pass — PostgreSQL as a hash right anti join over two
 * sequential scans, MySQL by materializing each subquery with deduplication once — so the cost is one
 * sequential scan of each referencing table, not one per message. Measured at production scale (615k
 * messages, 31.7M test-case and 2.5M SCA rows, 130k collected): 2.8 s on PostgreSQL 18, 15 s on MySQL 9.
 * <p>
 * Operational note: on MySQL's default REPEATABLE READ the {@code NOT EXISTS} subqueries of the delete are
 * locking reads, so InnoDB holds shared locks over the scanned referencing rows for the duration of the
 * statement. Run the orphan cleanup outside peak build times - concurrent build-result processing inserts
 * into exactly those tables and would wait. This is why the collection is admin-triggered and not scheduled.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FeedbackMessageCleanupRepository extends ArtemisJpaRepository<FeedbackMessage, Long> {

    /**
     * Deletes {@link FeedbackMessage} rows that are no longer referenced by any test-case or SCA feedback
     * and whose grace timestamp (set on creation, refreshed on every reuse) is older than the given cutoff.
     * The cutoff is the race-safety window: build-result processing commits the message row before the
     * feedback rows that reference it (there is no surrounding transaction), so a freshly created or
     * freshly reused message may look unreferenced for a moment.
     *
     * @param createdBefore only messages created before this timestamp are deleted
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM FeedbackMessage m
            WHERE m.createdDate < :createdBefore
                AND NOT EXISTS (SELECT 1 FROM TestCaseFeedback f WHERE f.message = m)
                AND NOT EXISTS (SELECT 1 FROM ScaFeedback f2 WHERE f2.message = m)
            """)
    int deleteUnreferencedFeedbackMessages(@Param("createdBefore") ZonedDateTime createdBefore);

    /**
     * Counts what {@link #deleteUnreferencedFeedbackMessages} would delete. The admin cleanup page disables its
     * execute button while every count is zero, so without this the garbage collection could never be triggered.
     *
     * @param createdBefore only messages created before this timestamp are counted, see the delete above
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(m)
            FROM FeedbackMessage m
            WHERE m.createdDate < :createdBefore
                AND NOT EXISTS (SELECT 1 FROM TestCaseFeedback f WHERE f.message = m)
                AND NOT EXISTS (SELECT 1 FROM ScaFeedback f2 WHERE f2.message = m)
            """)
    int countUnreferencedFeedbackMessages(@Param("createdBefore") ZonedDateTime createdBefore);
}
