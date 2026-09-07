package de.tum.cit.aet.artemis.core.service.featureusage;

import java.time.LocalDate;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * What one node accumulated for one bucket since its previous flush.
 * <p>
 * The counts are deltas, so the flush can add them to whatever is already stored without reading it first, which is what
 * makes several nodes writing the same bucket safe. {@code durationMaxMs} is the running maximum rather than a delta,
 * because the stored value is updated to the greater of the two and that is idempotent.
 *
 * @param featureId     the feature the counters belong to
 * @param usageDay      the UTC day of the bucket
 * @param callerRole    the caller's highest global role
 * @param callCount     calls since the previous flush
 * @param errorCount    failed calls since the previous flush
 * @param durationSumMs milliseconds spent since the previous flush
 * @param durationMaxMs the slowest call seen on this node so far
 */
public record FeatureUsageDelta(long featureId, LocalDate usageDay, Role callerRole, long callCount, long errorCount, long durationSumMs, int durationMaxMs) {
}
