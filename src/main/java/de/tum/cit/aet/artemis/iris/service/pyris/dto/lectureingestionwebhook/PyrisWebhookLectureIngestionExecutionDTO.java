package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookLectureIngestionExecutionDTO(PyrisLectureUnitWebhookDTO pyrisLectureUnit, long lectureUnitId, PyrisPipelineExecutionSettingsDTO settings) {
}
