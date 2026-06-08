package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContextDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for executing a chat pipeline in Pyris.
 *
 * @param chatMode                      the chat mode (course, exercise, lecture)
 * @param chatHistory                   the chat history messages
 * @param settings                      pipeline execution settings
 * @param sessionTitle                  optional session title
 * @param user                          the user
 * @param initialStages                 initial pipeline stages
 * @param customInstructions            optional custom instructions
 * @param course                        the course data
 * @param programmingExercise           optional programming exercise data
 * @param textExercise                  optional text exercise data
 * @param lecture                       optional lecture data
 * @param lectureUnitId                 optional lecture unit ID (deprecated, use context list)
 * @param programmingExerciseSubmission optional programming exercise submission
 * @param textExerciseSubmission        optional text exercise submission
 * @param metrics                       optional student metrics
 * @param context                       optional list of context objects providing information about what the user is viewing (not persisted, only sent to Pyris)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisChatPipelineExecutionDTO(IrisChatMode chatMode, List<PyrisMessageDTO> chatHistory, PyrisPipelineExecutionSettingsDTO settings, @Nullable String sessionTitle,
        PyrisUserDTO user, List<PyrisStageDTO> initialStages, @Nullable String customInstructions, PyrisCourseDTO course, @Nullable PyrisProgrammingExerciseDTO programmingExercise,
        @Nullable PyrisTextExerciseDTO textExercise, @Nullable PyrisLectureDTO lecture, @Nullable Long lectureUnitId, @Nullable PyrisSubmissionDTO programmingExerciseSubmission,
        @Nullable String textExerciseSubmission, @Nullable StudentMetricsDTO metrics, @Nullable List<IrisMessageContextDTO> context) {
}
