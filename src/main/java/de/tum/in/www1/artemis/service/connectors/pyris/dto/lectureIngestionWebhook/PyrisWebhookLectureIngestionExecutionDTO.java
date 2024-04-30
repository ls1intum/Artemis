package de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureIngestionWebhook;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public record PyrisWebhookLectureIngestionExecutionDTO(List<PyrisLectureUnitWebhookDTO> pyrisLectureUnitWebhookDTOS, PyrisPipelineExecutionSettingsDTO settings,
        List<PyrisStageDTO> initialStages) {
}
