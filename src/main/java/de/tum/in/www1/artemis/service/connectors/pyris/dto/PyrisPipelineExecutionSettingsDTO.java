package de.tum.in.www1.artemis.service.connectors.pyris.dto;

import java.util.List;

/**
 * DTO representing the settings required to execute a Pyris pipeline.
 *
 * @param authenticationToken     the authentication token to use for callbacks
 * @param allowedModelIdentifiers the allowed model identifiers
 * @param artemisBaseUrl          the base URL of the Artemis instance
 */
public record PyrisPipelineExecutionSettingsDTO(String authenticationToken, List<String> allowedModelIdentifiers, String artemisBaseUrl) {
}
