package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.autonomoustutor;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisPostDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Represents the execution of a pipeline for an autonomous tutor responding to student messages in communication threads.
 *
 * @param course              the course the post belongs to
 * @param post                the post/thread to respond to, including existing answers
 * @param user                the student whose message triggered the response
 * @param settings            pipeline execution settings
 * @param initialStages       initial stages of the pipeline for progress tracking
 * @param programmingExercise programming exercise if the channel is linked to one
 * @param textExercise        text exercise if the channel is linked to one
 * @param lecture             lecture if the channel is linked to one
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisAutonomousTutorPipelineExecutionDTO(PyrisCourseDTO course, PyrisPostDTO post, PyrisUserDTO user, PyrisPipelineExecutionSettingsDTO settings,
        List<PyrisStageDTO> initialStages, @Nullable PyrisProgrammingExerciseDTO programmingExercise, @Nullable PyrisTextExerciseDTO textExercise,
        @Nullable PyrisLectureDTO lecture) {
}
