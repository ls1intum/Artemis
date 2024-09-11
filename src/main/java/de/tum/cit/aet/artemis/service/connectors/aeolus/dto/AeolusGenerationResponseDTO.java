package de.tum.cit.aet.artemis.service.connectors.aeolus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Aeolus generation responses
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AeolusGenerationResponseDTO(String key, String result) {
}
