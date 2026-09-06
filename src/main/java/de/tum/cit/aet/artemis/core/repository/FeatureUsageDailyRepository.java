package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.FeatureUsageDaily;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Write access to the daily usage buckets.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FeatureUsageDailyRepository extends ArtemisJpaRepository<FeatureUsageDaily, Long> {

    /**
     * Adds one node's accumulated delta to an existing bucket.
     * <p>
     * This is an additive update rather than a read-modify-write, so several nodes flushing the same bucket at the same
     * time cannot lose each other's counts. It is also plain JPQL rather than {@code ON CONFLICT} or
     * {@code ON DUPLICATE KEY UPDATE}, because Artemis runs on both PostgreSQL and MySQL and one portable statement is
     * worth more here than the single round trip an upsert would save.
     * <p>
     * The caller inserts the row when this returns 0. That is the only time an insert happens for a given bucket, so
     * the steady state is pure updates.
     *
     * @param featureId     the feature the counters belong to
     * @param usageDay      the UTC day of the bucket
     * @param callerRole    the caller role of the bucket
     * @param callCount     calls to add
     * @param errorCount    failed calls to add
     * @param durationSumMs milliseconds to add to the total
     * @param durationMaxMs candidate for the new maximum
     * @return 1 if the bucket existed and was updated, 0 if it does not exist yet
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE FeatureUsageDaily bucket
            SET bucket.callCount = bucket.callCount + :callCount,
                bucket.errorCount = bucket.errorCount + :errorCount,
                bucket.durationSumMs = bucket.durationSumMs + :durationSumMs,
                bucket.durationMaxMs = CASE WHEN bucket.durationMaxMs < :durationMaxMs THEN :durationMaxMs ELSE bucket.durationMaxMs END
            WHERE bucket.featureId = :featureId
                AND bucket.usageDay = :usageDay
                AND bucket.callerRole = :callerRole
            """)
    int addUsage(@Param("featureId") long featureId, @Param("usageDay") LocalDate usageDay, @Param("callerRole") Role callerRole, @Param("callCount") long callCount,
            @Param("errorCount") long errorCount, @Param("durationSumMs") long durationSumMs, @Param("durationMaxMs") int durationMaxMs);

    /**
     * Deletes every bucket older than the given day. A bulk delete is safe here because the table has no element
     * collections and nothing references it.
     *
     * @param cutoff the first day to keep
     * @return the number of deleted rows
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM FeatureUsageDaily bucket
            WHERE bucket.usageDay < :cutoff
            """)
    int deleteAllOlderThan(@Param("cutoff") LocalDate cutoff);
}
