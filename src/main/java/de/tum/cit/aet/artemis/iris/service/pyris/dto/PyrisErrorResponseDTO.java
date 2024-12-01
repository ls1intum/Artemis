package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing an error response from Pyris.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisErrorResponseDTO(String errorMessage) {
}
