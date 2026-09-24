package de.tum.cit.aet.artemis.admin.dto;

import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.service.featureusage.ProductArea;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * The usage of one user-facing feature over a window, summed over every endpoint and module that serves it.
 * <p>
 * Every feature of the catalogue gets an entry, including those nobody used and those this deployment does not offer,
 * because both are findings. Automatic and system calls are counted separately and never contribute to use: the error
 * count, the durations and the active days only cover actions and views.
 *
 * @param feature            the catalogue entry
 * @param area               the product area it is listed under
 * @param status             what the usage amounts to
 * @param noActions          the feature has endpoints that act and was only viewed. Reported as a flag rather than as a
 *                               status, because for a feature that people mostly read, such as the notifications or the
 *                               FAQs, viewing is use, while for an authoring aid it means nobody took the step it is for.
 * @param actionCount        calls that did something
 * @param viewCount          calls that looked at something
 * @param automaticCount     calls the client made on its own
 * @param systemCount        calls by other systems
 * @param errorCount         failed actions and views
 * @param durationSumMs      total time spent on actions and views
 * @param durationMaxMs      the slowest action or view
 * @param activeDays         days with at least one action or view
 * @param actionDays         days with at least one action
 * @param lastUsedDay        the most recent day with an action or view, absent if there was none in the window
 * @param lastActionDay      the most recent day with an action, absent if there was none in the window
 * @param modules            the modules whose endpoints serve the feature
 * @param endpointCount      how many endpoints and recorded operations currently serve the feature
 * @param hasActionEndpoints whether any of them is an action, which is what makes {@code noActions} meaningful
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserFeatureUsageDTO(UserFeature feature, ProductArea area, FeatureUsageStatus status, boolean noActions, long actionCount, long viewCount, long automaticCount,
        long systemCount, long errorCount, long durationSumMs, int durationMaxMs, long activeDays, long actionDays, @Nullable LocalDate lastUsedDay,
        @Nullable LocalDate lastActionDay, List<String> modules, int endpointCount, boolean hasActionEndpoints) {
}
