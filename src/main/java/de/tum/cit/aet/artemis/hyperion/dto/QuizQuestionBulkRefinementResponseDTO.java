package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for a bulk quiz question refinement response.
 * Contains one refinement result per input question, in the same order.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing one refinement result per input question, in the same order")
public record QuizQuestionBulkRefinementResponseDTO(
        @NotNull @Valid @Schema(description = "Refinement results, one per input question in the same order") List<QuizQuestionRefinementResponseDTO> refinements) {
}
