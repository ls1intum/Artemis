package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

/**
 * DTO representing the settings required to execute a Pyris pipeline.
 *
 * @param authenticationToken the authentication token to use for callbacks
 * @param selection           the selection between cloud or local LLMs
 * @param artemisBaseUrl      the base URL of the Artemis instance
 * @param variant             the variant of the pipeline to execute
 * @param supportLevel        the instructional support level ("low" / "moderate" / "high")
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPipelineExecutionSettingsDTO(String authenticationToken, AiSelectionDecision selection, String artemisBaseUrl, String variant, String supportLevel) {
}
