package de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Status update DTO sent from Pyris with the generated response for the autonomous tutor pipeline.
 *
 * @param result             the generated response to the student's post
 * @param shouldPostDirectly whether the response should be posted directly to the student
 * @param confidence         confidence score (0.0 to 1.0) indicating how confident the model is in the response
 * @param stages             stages of the pipeline
 * @param tokens             tokens used for generating the response
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisAutonomousTutorPipelineStatusUpdateDTO(@Nullable String result, boolean shouldPostDirectly, @Nullable Double confidence, List<PyrisStageDTO> stages,
        List<LLMRequest> tokens) {
}
