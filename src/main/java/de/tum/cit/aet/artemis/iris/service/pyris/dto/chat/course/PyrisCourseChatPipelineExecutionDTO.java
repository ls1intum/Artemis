package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.course;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.dto.CompetencyJolDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisExtendedCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseChatPipelineExecutionDTO(PyrisExtendedCourseDTO course, StudentMetricsDTO metrics, CompetencyJolDTO competencyJol, List<PyrisMessageDTO> chatHistory,
        PyrisUserDTO user, PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages) {
}
