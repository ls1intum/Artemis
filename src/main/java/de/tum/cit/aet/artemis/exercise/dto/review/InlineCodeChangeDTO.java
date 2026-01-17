package de.tum.cit.aet.artemis.exercise.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Inline code change suggestion that can be applied to a specific line range.")
public record InlineCodeChangeDTO(@Schema(description = "First line number (inclusive) of the replacement range.") Integer startLine,
        @Schema(description = "Last line number (inclusive) of the replacement range.") Integer endLine,
        @Schema(description = "Code expected in the replacement range before applying the change.") String expectedCode,
        @Schema(description = "Code that replaces the expected code within the range.") String replacementCode,
        @Schema(description = "Whether the change has been applied.") Boolean applied) {
}
