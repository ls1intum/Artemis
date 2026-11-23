package de.tum.cit.aet.artemis.hyperion.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight candidate metadata for quiz question allocation policies.
 * Omits full question JSON to enable low-overhead candidate filtering and sampling.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Lightweight quiz question pool candidate metadata for allocation")
public record QuizQuestionPoolCandidateDTO(long id, @Nullable Long competencyId, @Nullable QuizQuestionDifficulty difficulty) {
}
