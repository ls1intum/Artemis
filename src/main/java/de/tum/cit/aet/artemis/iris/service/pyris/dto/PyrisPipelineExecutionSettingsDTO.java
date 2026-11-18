package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

/**
 * DTO representing the settings required to execute a Pyris pipeline.
 *
 * @param runId               the id of the run; not a secret, Pyris uses it in callback URLs and logs
 * @param authenticationToken the token Pyris sends as Bearer credential on every callback for this run; a secret
 * @param selection           the selection between cloud or local LLMs
 * @param artemisBaseUrl      the base URL of the Artemis instance
 * @param variant             the variant of the pipeline to execute
 * @param supportLevel        the instructional support level ("low" / "moderate" / "high")
 * @param streamResponse      whether Pyris should send partial response updates during execution
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPipelineExecutionSettingsDTO(String runId, String authenticationToken, AiSelectionDecision selection, String artemisBaseUrl, String variant, String supportLevel,
        @Nullable Boolean streamResponse) {

    public PyrisPipelineExecutionSettingsDTO(String runId, String authenticationToken, AiSelectionDecision selection, String artemisBaseUrl, String variant, String supportLevel) {
        this(runId, authenticationToken, selection, artemisBaseUrl, variant, supportLevel, null);
    }

    /**
     * Leaves out the token, so that logging this DTO, or any DTO that contains it, does not write the credential into the log.
     *
     * @return a description of the settings without the token
     */
    @Override
    public String toString() {
        return "PyrisPipelineExecutionSettingsDTO[runId=" + runId + ", authenticationToken=<hidden>, selection=" + selection + ", artemisBaseUrl=" + artemisBaseUrl + ", variant="
                + variant + ", supportLevel=" + supportLevel + ", streamResponse=" + streamResponse + "]";
    }
}
