package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One module's usage over the digest window.
 * <p>
 * Retired features are excluded from all the counts here. A module is not "half unused" because endpoints it stopped
 * offering two releases ago no longer get called.
 *
 * @param module            the Artemis module
 * @param callCount         calls over the window
 * @param previousCallCount calls over the equally long window before it, so the email can show a direction rather than
 *                              just a number. A weekly mail that looks identical every week stops being read.
 * @param errorCount        failed calls over the window
 * @param usedFeatures      features of this module that were used at least once
 * @param trackedFeatures   features of this module that this version still offers
 * @param unusedFeatures    features still offered that saw no use, the actionable figure
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageModuleSummaryDTO(String module, long callCount, long previousCallCount, long errorCount, long usedFeatures, long trackedFeatures, long unusedFeatures) {

    /**
     * The change against the previous window, in percent, or {@code null} when there is nothing to compare against.
     * <p>
     * Returned as a boxed value on purpose: "no previous data" and "no change" mean quite different things in a digest, and
     * a sentinel number would blur them.
     *
     * @return the signed percentage change, or {@code null} if the previous window saw no calls
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Long changePercent() {
        if (previousCallCount == 0) {
            return null;
        }
        return Math.round((callCount - previousCallCount) * 100.0 / previousCallCount);
    }
}
