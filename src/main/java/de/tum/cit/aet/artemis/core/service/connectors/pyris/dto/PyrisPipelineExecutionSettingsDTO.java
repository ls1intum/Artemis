package de.tum.cit.aet.artemis.core.service.connectors.pyris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the settings required to execute a Pyris pipeline.
 *
 * @param authenticationToken     the authentication token to use for callbacks
 * @param allowedModelIdentifiers the allowed model identifiers
 * @param artemisBaseUrl          the base URL of the Artemis instance
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPipelineExecutionSettingsDTO(String authenticationToken, List<String> allowedModelIdentifiers, String artemisBaseUrl) {
}
