package de.tum.cit.aet.artemis.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.service.featureusage.ProductArea;

/**
 * The weekly feature usage summary that goes out by email.
 * <p>
 * Deliberately a summary and not a copy of the admin page. The email exists to make someone open the page, so it carries
 * the few numbers that tell them whether it is worth doing. Use means actions and views throughout; automatic calls are
 * only reported as the number of features that received nothing else.
 *
 * @param days              the length of the window
 * @param from              the first day covered
 * @param to                the last day covered
 * @param useCount          actions and views across all features in the window
 * @param previousUseCount  actions and views over the equally long window before it
 * @param availableFeatures features this deployment offers
 * @param usedFeatures      of those, how many saw an action or a view
 * @param unusedFeatures    of those, how many saw neither, which is the number the page is really about
 * @param onlyAutomatic     of the unused ones, how many only received automatic calls, which is what used to make an unused
 *                              feature look busy
 * @param retiredEndpoints  inventory entries this version no longer offers, reported separately so they cannot be mistaken
 *                              for a backlog
 * @param recordingSince    when this deployment started recording, so a young instance cannot imply a year of evidence
 * @param activeAreas       areas with an action or a view in this window or the one before, busiest first, so that a drop to
 *                              zero shows as a row
 * @param quietAreas        areas that offer features but saw no use in either window. Listed by name rather than as rows,
 *                              because the interesting thing about them is only that they are on the list.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageDigestDTO(int days, LocalDate from, LocalDate to, long useCount, long previousUseCount, long availableFeatures, long usedFeatures, long unusedFeatures,
        long onlyAutomatic, long retiredEndpoints, @Nullable Instant recordingSince, List<FeatureUsageAreaSummaryDTO> activeAreas, List<ProductArea> quietAreas) {

    /**
     * Whether anything at all was used in the window. A digest with no data at all reads as a broken deployment, so the
     * template says so explicitly instead of showing a table of zeros.
     *
     * @return true if no action and no view was recorded
     */
    public boolean isEmpty() {
        return useCount == 0;
    }
}
