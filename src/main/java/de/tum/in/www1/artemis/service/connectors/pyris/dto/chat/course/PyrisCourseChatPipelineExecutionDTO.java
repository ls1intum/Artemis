package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.course;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatPipelineExecutionBaseDataDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisExtendedCourseDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.StudentMetricsDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseChatPipelineExecutionDTO(PyrisChatPipelineExecutionBaseDataDTO base, PyrisExtendedCourseDTO course, StudentMetricsDTO metrics) {
}
