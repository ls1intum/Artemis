package de.tum.cit.aet.artemis.athena.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;

/**
 * DTO representing the meta information in the Athena response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseMetaDTO(TotalUsage totalUsage, List<LLMRequest> llmRequests) {

    public record TotalUsage(Integer numInputTokens, Integer numOutputTokens, Integer numTotalTokens, Float cost) {
    }
}
