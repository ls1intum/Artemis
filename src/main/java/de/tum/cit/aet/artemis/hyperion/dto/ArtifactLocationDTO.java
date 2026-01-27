package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for artifact location information.
 *
 * @param type      Type of artifact
 * @param filePath  Path to file, empty or problem_statement.md for problem
 *                      statement
 * @param startLine Start line number (1-based)
 * @param endLine   End line number (1-based)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Location information for artifacts")
public record ArtifactLocationDTO(

        @NotNull @Schema(description = "Type of artifact", example = "PROBLEM_STATEMENT") ArtifactType type,

        @NotNull @Schema(description = "Path to file, empty or problem_statement.md for problem statement", example = "src/main/java/Main.java") String filePath,

        @NotNull @Schema(description = "Start line number (1-based)") Integer startLine,

        @NotNull @Schema(description = "End line number (1-based)") Integer endLine) {
}
