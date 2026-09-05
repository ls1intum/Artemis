package de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;

/**
 * Execution payload for the Pyris struggle-intervention pipeline. {@code settings} is
 * top-level (Pyris hoists it as a sibling). Field names map 1:1 to Plan 1's pydantic
 * {@code StruggleInterventionPipelineExecutionDTO}. {@code chatHistory} is empty when no exercise
 * session exists yet (deferred materialization).
 * <p>
 * {@code intent} carries the slot action ({@code decide} | {@code confirm_close}).
 * {@code episode} is the client-allocated episode block; the {@link StruggleEpisodeDTO} uses bare
 * {@code @JsonInclude()} (Include.ALWAYS) so an empty {@code hints:[]} is always serialized -- NON_EMPTY
 * would drop it and break the Pyris contract on the first FREE-slot decide.
 * <p>
 * {@code proactivityMode} ({@code pull} | {@code push}) is passed to Pyris purely as prompt tone context
 * (reticent vs. willing to reach out); the hard Pull guarantee is enforced deterministically in Artemis
 * ({@code handleDecision}), not by the LLM. Serialized snake_case for the Pyris boundary.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStruggleInterventionPipelineExecutionDTO(PyrisStruggleSignalDTO struggleSignal, @Nullable PyrisProgrammingExerciseDTO programmingExercise,
        @Nullable PyrisSubmissionDTO programmingExerciseSubmission, List<PyrisMessageDTO> chatHistory, @Nullable PyrisCourseDTO course, @Nullable PyrisUserDTO user,
        PyrisPipelineExecutionSettingsDTO settings, @Nullable String intent, @Nullable StruggleEpisodeDTO episode,
        @JsonProperty("proactivity_mode") @Nullable String proactivityMode) {
}
