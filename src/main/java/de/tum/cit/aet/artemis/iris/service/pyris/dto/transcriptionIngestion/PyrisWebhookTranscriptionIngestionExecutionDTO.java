package de.tum.cit.aet.artemis.iris.service.pyris.dto.transcriptionIngestion;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookTranscriptionIngestionExecutionDTO(PyrisTranscriptionIngestionWebhookDTO transcription, Long lectureUnitId, PyrisPipelineExecutionSettingsDTO settings) {
}
