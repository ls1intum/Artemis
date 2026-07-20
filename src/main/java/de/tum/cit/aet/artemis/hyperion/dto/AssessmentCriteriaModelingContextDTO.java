package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Modeling-specific context for assessment criteria generation.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Modeling-specific exercise context")
public record AssessmentCriteriaModelingContextDTO(@NotBlank @Size(max = 255) @Schema(description = "UML diagram type") String diagramType,
        @Nullable @Size(max = 200_000) @Schema(description = "Serialized current example solution model") String exampleSolutionModel) {
}
