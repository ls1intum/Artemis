package de.tum.cit.aet.artemis.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The weekly feature usage summary that goes out by email.
 * <p>
 * Deliberately a summary and not a copy of the admin page. The email exists to make someone open the page, so it carries
 * the few numbers that tell them whether it is worth doing.
 *
 * @param days               the length of the window
 * @param from               the first day covered
 * @param to                 the last day covered
 * @param totalCalls         calls across all features in the window
 * @param previousTotalCalls calls over the equally long window before it
 * @param trackedFeatures    features this version offers
 * @param usedFeatures       of those, how many were used at least once
 * @param unusedFeatures     of those, how many were not, which is the number the page is really about
 * @param retiredFeatures    inventory entries this version no longer offers, reported separately so they cannot be mistaken
 *                               for a backlog
 * @param recordingSince     when this deployment started recording, so a young instance cannot imply a year of evidence
 * @param activeModules      modules with at least one call, busiest first
 * @param quietModules       modules that still offer features but saw no call at all. Listed by name rather than as rows,
 *                               because the interesting thing about them is only that they are on the list.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageDigestDTO(int days, LocalDate from, LocalDate to, long totalCalls, long previousTotalCalls, long trackedFeatures, long usedFeatures, long unusedFeatures,
        long retiredFeatures, @Nullable Instant recordingSince, List<FeatureUsageModuleSummaryDTO> activeModules, List<String> quietModules) {

    /**
     * Whether anything at all was recorded in the window. A digest with no data at all reads as a broken deployment, so the
     * template says so explicitly instead of showing a table of zeros.
     *
     * @return true if no call was recorded
     */
    public boolean isEmpty() {
        return totalCalls == 0;
    }
}
