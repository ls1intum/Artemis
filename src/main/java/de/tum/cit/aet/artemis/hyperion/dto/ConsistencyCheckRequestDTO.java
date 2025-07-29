package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for consistency check requests.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConsistencyCheckRequestDTO(Long exerciseId) {
}
