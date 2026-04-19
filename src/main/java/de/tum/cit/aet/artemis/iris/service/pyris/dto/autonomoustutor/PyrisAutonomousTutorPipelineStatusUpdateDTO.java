package de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Status update DTO sent from Pyris with the generated response for the autonomous tutor pipeline.
 * Artemis decides what to do with the response based purely on {@code confidence}:
 * {@code >= 0.95} auto-publishes, {@code [0.80, 0.95)} holds the reply for tutor review, and
 * everything below {@code 0.80} (or missing) is discarded.
 *
 * @param result     the generated response to the student's post
 * @param confidence confidence score (0.0 to 1.0) indicating how confident the model is in the response
 * @param stages     stages of the pipeline
 * @param tokens     tokens used for generating the response
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PyrisAutonomousTutorPipelineStatusUpdateDTO(@Nullable String result, @Nullable Double confidence, @NonNull List<PyrisStageDTO> stages,
        @NonNull List<LLMRequest> tokens) {

    public PyrisAutonomousTutorPipelineStatusUpdateDTO {
        stages = stages != null ? stages : List.of();
        tokens = tokens != null ? tokens : List.of();
    }
}
