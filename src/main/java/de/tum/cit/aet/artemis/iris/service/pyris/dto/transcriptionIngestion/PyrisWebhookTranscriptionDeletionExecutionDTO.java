package de.tum.cit.aet.artemis.iris.service.pyris.dto.transcriptionIngestion;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookTranscriptionDeletionExecutionDTO(PyrisTranscriptionIngestionWebhookDTO lectureTranscriptionDTO, PyrisPipelineExecutionSettingsDTO settings,
        List<PyrisStageDTO> initialStages) {
}
