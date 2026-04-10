package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PyrisChatPipelineExecutionDTO(IrisChatMode chatMode, List<PyrisMessageDTO> chatHistory, PyrisPipelineExecutionSettingsDTO settings, @Nullable String sessionTitle,
        @Nullable PyrisUserDTO user, @Nullable List<PyrisStageDTO> initialStages, @Nullable String customInstructions, @Nullable PyrisCourseDTO course, @Nullable Object exercise,
        @Nullable PyrisLectureDTO lecture, @Nullable Long lectureUnitId, @Nullable PyrisSubmissionDTO programmingExerciseSubmission, @Nullable String textExerciseSubmission,
        @Nullable StudentMetricsDTO metrics, @Nullable PyrisEventDTO<?> eventPayload) {
}
