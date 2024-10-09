package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPipelineExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages) {
}
