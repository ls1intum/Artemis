package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the settings required to execute a Pyris pipeline.
 *
 * @param authenticationToken the authentication token to use for callbacks
 * @param artemisBaseUrl      the base URL of the Artemis instance
 * @param variant             the variant of the pipeline to execute
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPipelineExecutionSettingsDTO(String authenticationToken, String artemisBaseUrl, String variant) {
}
