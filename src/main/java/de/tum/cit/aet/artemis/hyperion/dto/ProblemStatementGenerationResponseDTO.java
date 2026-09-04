package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement generation responses.
 * <p>
 * On success, {@code draftProblemStatement} contains the generated text (never null or empty).
 * Errors are communicated via standard Spring error responses (e.g. {@code 400}, {@code 500}).
 *
 * @param draftProblemStatement the generated problem statement text (non-empty on success, never null)
 * @param hygieneWarnings       advisory hygiene warnings about the draft that should be reviewed before use; the draft is still returned and applied
 *                                  regardless. Omitted from the response when empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing generated problem statement")
public record ProblemStatementGenerationResponseDTO(@Schema(description = "Draft problem statement text") String draftProblemStatement,
        @Schema(description = "Advisory hygiene warnings about the draft to review before use; omitted when there are none") List<String> hygieneWarnings) {
}
