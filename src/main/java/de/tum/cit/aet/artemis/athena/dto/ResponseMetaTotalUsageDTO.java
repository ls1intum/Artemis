package de.tum.cit.aet.artemis.athena.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the total usage metrics.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseMetaTotalUsageDTO(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
}
