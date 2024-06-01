package de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public record PyrisWebhookLectureIngestionExecutionDTO(@JsonProperty("lectureUnits") List<PyrisLectureUnitWebhookDTO> pyrisLectureUnitWebhookDTOS,
        PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages) {
}
