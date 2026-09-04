package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request for the metadata of the exercise an instructor's brief describes.
 *
 * @param prompt      the instructor's brief, bounded by the same length as {@link ExerciseGenerationRequestDTO#prompt()} because it is the same text
 * @param projectType the project type the exercise will be created with, which decides the shape of the proposed package name; null is treated as a dotted-package project, so a
 *                        client that has not chosen a build tool yet still gets a complete suggestion
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request for the derived metadata of an exercise an instructor is about to generate")
public record ExerciseGenerationMetadataSuggestionRequestDTO(
        @NotBlank @Size(max = 8000) @Schema(description = "The instructor's brief for the exercise to be generated") String prompt,
        @Nullable @Schema(description = "The project type the exercise will use") ProjectType projectType) {
}
