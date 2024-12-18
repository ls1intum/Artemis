package de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookFaqDeletionExecutionDTO(List<PyrisFaqWebhookDTO> pyrisLectureUnits, PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages) {
}
