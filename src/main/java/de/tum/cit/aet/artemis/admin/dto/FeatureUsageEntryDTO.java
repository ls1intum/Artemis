package de.tum.cit.aet.artemis.admin.dto;

import java.time.Instant;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.FeatureKind;

/**
 * One feature and what it was used for over the selected window.
 * <p>
 * A feature nobody touched still gets an entry, with zero counts and no {@code lastUsedDay}. That is the point of the
 * report, so those rows must not be filtered out on the way here.
 *
 * @param featureId        the inventory row this describes
 * @param featureKind      whether this is a REST endpoint, a git operation or a background feature
 * @param module           the Artemis module it belongs to
 * @param identifier       the canonical identifier, for a REST feature the HTTP verb and templated path
 * @param featureLabel     the {@code @FeatureUsage} label, absent when the feature is unlabelled
 * @param callCount        calls over the window
 * @param errorCount       failed calls over the window
 * @param durationSumMs    total time spent, from which the client derives the mean
 * @param durationMaxMs    the slowest single call over the window
 * @param activeDays       days on which the feature was used at least once, which separates steady use from a single burst
 * @param lastUsedDay      the most recent day with a call, absent if there was none in the window
 * @param lastRegisteredAt the last time a server reported that this feature still exists
 * @param retired          whether this version no longer offers the feature at all, so its zero usage needs no decision.
 *                             Decided on the server rather than left to the client, so there is one definition of retired
 *                             and the headline counts and the table cannot disagree.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageEntryDTO(long featureId, FeatureKind featureKind, String module, String identifier, @Nullable String featureLabel, long callCount, long errorCount,
        long durationSumMs, int durationMaxMs, long activeDays, @Nullable LocalDate lastUsedDay, Instant lastRegisteredAt, boolean retired) {

    /**
     * Constructor used by the aggregate query, which cannot know yet whether the feature is retired.
     */
    public FeatureUsageEntryDTO(long featureId, FeatureKind featureKind, String module, String identifier, @Nullable String featureLabel, long callCount, long errorCount,
            long durationSumMs, int durationMaxMs, long activeDays, @Nullable LocalDate lastUsedDay, Instant lastRegisteredAt) {
        this(featureId, featureKind, module, identifier, featureLabel, callCount, errorCount, durationSumMs, durationMaxMs, activeDays, lastUsedDay, lastRegisteredAt, false);
    }

    /**
     * Builds the variant of this entry that knows whether the feature is retired. The aggregate query cannot decide that,
     * because it depends on the newest registration timestamp across the whole inventory.
     *
     * @param retired whether this version no longer offers the feature
     * @return a copy carrying the flag
     */
    public FeatureUsageEntryDTO withRetired(boolean retired) {
        return new FeatureUsageEntryDTO(featureId, featureKind, module, identifier, featureLabel, callCount, errorCount, durationSumMs, durationMaxMs, activeDays, lastUsedDay,
                lastRegisteredAt, retired);
    }
}
