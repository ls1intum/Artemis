package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Calls of one module over a day range. Only used internally, to supply the digest with its comparison figure.
 *
 * @param module    the Artemis module
 * @param callCount calls over the range
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageModuleCallsDTO(String module, long callCount) {
}
