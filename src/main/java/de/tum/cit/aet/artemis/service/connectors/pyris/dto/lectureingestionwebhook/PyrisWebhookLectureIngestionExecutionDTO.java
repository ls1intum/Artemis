package de.tum.cit.aet.artemis.service.connectors.pyris.dto.lectureingestionwebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookLectureIngestionExecutionDTO(List<PyrisLectureUnitWebhookDTO> pyrisLectureUnitWebhookDTOS, PyrisPipelineExecutionSettingsDTO settings,
        List<PyrisStageDTO> initialStages) {
}
