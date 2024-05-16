package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.course;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatPipelineExecutionBaseDataDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisExtendedCourseDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.StudentMetricsDTO;

public record PyrisCourseChatPipelineExecutionDTO(PyrisChatPipelineExecutionBaseDataDTO base, PyrisExtendedCourseDTO course, StudentMetricsDTO metrics) {
}
