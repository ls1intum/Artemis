package de.tum.cit.aet.artemis.athena.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing an individual LLM call.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseMetaLLMCallDTO(String modelName, Integer inputTokens, Integer outputTokens, Integer totalTokens) {
}
