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

/**
 * DTO for executing the course chat pipeline on Pyris.
 *
 * @param course        the course being chatted about
 * @param metrics       some metrics about the user
 * @param competencyJol the competency judgement-of-learning, if any. TODO: Could wrap in Optional for API clarity
 * @param chatHistory   the conversation history so far (including the user's latest message)
 * @param user          the user
 * @param settings      execution settings
 * @param initialStages initial stages from pipeline preparation
 */
// TODO: Instead of including settings and initialStages, include PyrisPipelineExecutionDTO
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// @formatter:off
public record PyrisCourseChatPipelineExecutionDTO(
        PyrisExtendedCourseDTO course,
        StudentMetricsDTO metrics,
        CompetencyJolDTO competencyJol,
        List<PyrisMessageDTO> chatHistory,
        PyrisUserDTO user,
        PyrisPipelineExecutionSettingsDTO settings,
        List<PyrisStageDTO> initialStages
) {}
// @formatter:on
