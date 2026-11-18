package de.tum.cit.aet.artemis.admin.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;

/**
 * Calls of one feature and interaction over a day range. Only used internally, to supply the digest with its comparison
 * figure.
 *
 * @param featureLabel the feature label, absent for an inventory row that carries none
 * @param interaction  how the calls count
 * @param callCount    calls over the range
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageLabelCallsDTO(@Nullable String featureLabel, FeatureInteraction interaction, long callCount) {
}
