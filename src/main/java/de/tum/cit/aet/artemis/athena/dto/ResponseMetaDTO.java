package de.tum.cit.aet.artemis.athena.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the meta information in the Athena response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseMetaDTO(ResponseMetaTotalUsageDTO totalUsage, List<ResponseMetaLLMCallDTO> llmCalls) {
}
