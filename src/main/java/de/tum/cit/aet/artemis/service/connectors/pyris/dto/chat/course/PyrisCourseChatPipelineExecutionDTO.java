package de.tum.cit.aet.artemis.service.connectors.pyris.dto.chat.course;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.data.PyrisExtendedCourseDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyJolDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.StudentMetricsDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseChatPipelineExecutionDTO(PyrisExtendedCourseDTO course, StudentMetricsDTO metrics, CompetencyJolDTO competencyJol, List<PyrisMessageDTO> chatHistory,
        PyrisUserDTO user, PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages) {
}
