package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookLectureDeletionExecutionDTO(List<PyrisLectureUnitWebhookDTO> pyrisLectureUnits, PyrisPipelineExecutionSettingsDTO settings) {
}
