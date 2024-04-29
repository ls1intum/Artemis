package de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureIngestionWebhook;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public final class PyrisWebhookLectureIngestionExecutionDTO extends PyrisPipelineExecutionDTO {

    private final List<PyrisLectureUnitWebhookDTO> lectureUnits;

    public PyrisWebhookLectureIngestionExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages,
            List<PyrisLectureUnitWebhookDTO> pyrisLectureUnitWebhookDTOS) {
        super(settings, initialStages);
        this.lectureUnits = pyrisLectureUnitWebhookDTOS;
    }

    public List<PyrisLectureUnitWebhookDTO> getLectureUnits() {
        return lectureUnits;
    }

}
