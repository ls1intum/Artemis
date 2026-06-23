package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Inline code change suggestion that can be applied to a specific line range.")
public record InlineCodeChangeDTO(@Schema(description = "First line number (inclusive) of the replacement range.") @NotNull Integer startLine,
        @Schema(description = "Last line number (inclusive) of the replacement range.") @NotNull Integer endLine,
        @Schema(description = "Code expected in the replacement range before applying the change.") @NotNull @Size(max = 10000) String expectedCode,
        @Schema(description = "Code that replaces the expected code within the range.") @NotNull @Size(max = 10000) String replacementCode,
        @Schema(description = "Whether the change has been applied.") @NotNull Boolean applied) {

    public InlineCodeChangeDTO {
        // @JsonInclude(NON_EMPTY) drops empty strings during serialization (e.g. an empty replacementCode
        // representing a pure deletion). Normalize them back on deserialization so the @NotNull contract
        // holds when the DTO is round-tripped (it is persisted as JSON comment content).
        expectedCode = expectedCode == null ? "" : expectedCode;
        replacementCode = replacementCode == null ? "" : replacementCode;
    }
}
