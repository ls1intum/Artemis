package de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookFaqIngestionExecutionDTO(PyrisFaqWebhookDTO pyrisFaqWebhookDTO, PyrisPipelineExecutionSettingsDTO settings) {
}
