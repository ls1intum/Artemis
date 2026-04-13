package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO received from the client for an Ask Iris search request.
 * The server adds artemisBaseUrl and authenticationToken before forwarding to Pyris.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisSearchAskClientRequestDTO(@NotBlank String query, @Min(1) @Max(5) int limit) {
}
