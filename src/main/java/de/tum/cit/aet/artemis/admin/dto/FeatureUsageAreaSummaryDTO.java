package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.service.featureusage.ProductArea;

/**
 * One product area's usage over the digest window.
 * <p>
 * Use means actions and views. Automatic calls are left out of every figure here, because a poll that the client sends on
 * every page load would otherwise make an area look busy that nobody used.
 *
 * @param area              the product area
 * @param useCount          actions and views over the window
 * @param previousUseCount  actions and views over the equally long window before it, so the email can show a direction
 *                              rather than just a number. A weekly mail that looks identical every week stops being read.
 * @param actionCount       of those, the actions
 * @param errorCount        failed actions and views over the window
 * @param usedFeatures      features of this area that were used at least once
 * @param availableFeatures features of this area that this deployment offers
 * @param onlyAutomatic     features of this area that received automatic calls only
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageAreaSummaryDTO(ProductArea area, long useCount, long previousUseCount, long actionCount, long errorCount, long usedFeatures, long availableFeatures,
        long onlyAutomatic) {

    /**
     * The change against the previous window, in percent, or {@code null} when there is nothing to compare against.
     * <p>
     * Returned as a boxed value on purpose: "no previous data" and "no change" mean quite different things in a digest, and
     * a sentinel number would blur them.
     *
     * @return the signed percentage change, or {@code null} if the previous window saw no use
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Long changePercent() {
        if (previousUseCount == 0) {
            return null;
        }
        return Math.round((useCount - previousUseCount) * 100.0 / previousUseCount);
    }

    /**
     * The features of this area that were offered and not used, which is the actionable figure.
     *
     * @return offered features without an action or a view
     */
    public long unusedFeatures() {
        return availableFeatures - usedFeatures;
    }
}
