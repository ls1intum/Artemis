package de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Execution payload for the Pyris struggle-intervention pipeline (spec §5.3). {@code settings} and
 * {@code initialStages} are top-level (Pyris hoists them as siblings). Field names map 1:1 to Plan 1's
 * pydantic {@code StruggleInterventionPipelineExecutionDTO}. {@code chatHistory} is empty when no exercise
 * session exists yet (deferred materialization, §11).
 * <p>
 * {@code intent} carries the slot action ({@code decide} | {@code confirm_close} | {@code stale_check}).
 * {@code episode} is the client-allocated episode block; the {@link StruggleEpisodeDTO} uses bare
 * {@code @JsonInclude()} (Include.ALWAYS) so an empty {@code hints:[]} is always serialized -- NON_EMPTY
 * would drop it and break the Pyris contract on the first FREE-slot decide.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStruggleInterventionPipelineExecutionDTO(PyrisStruggleSignalDTO struggleSignal, @Nullable PyrisProgrammingExerciseDTO programmingExercise,
        @Nullable PyrisSubmissionDTO programmingExerciseSubmission, List<PyrisMessageDTO> chatHistory, @Nullable PyrisCourseDTO course, @Nullable PyrisUserDTO user,
        PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages, @Nullable String intent, @Nullable StruggleEpisodeDTO episode) {
}
