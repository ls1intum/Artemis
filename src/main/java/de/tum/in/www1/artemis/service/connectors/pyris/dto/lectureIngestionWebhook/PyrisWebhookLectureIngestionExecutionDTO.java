package de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureIngestionWebhook;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;

public record PyrisWebhookLectureIngestionExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, List<PyrisLectureUnitWebhookDTO> lectureUnits) {
}
