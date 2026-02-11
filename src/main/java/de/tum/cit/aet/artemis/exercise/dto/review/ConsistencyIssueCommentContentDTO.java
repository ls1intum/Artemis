package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Content describing a detected consistency issue in a review.")
public record ConsistencyIssueCommentContentDTO(@Schema(description = "Severity level assigned to the issue.") @NotNull Severity severity,
        @Schema(description = "Category of the consistency issue.") @NotNull ConsistencyIssueCategory category,
        @Schema(description = "Human-readable description of the issue.") @NotBlank @Size(max = 10000) String text,
        @Schema(description = "Inline code change suggestion, if applicable.", nullable = true) InlineCodeChangeDTO suggestedFix) implements CommentContentDTO {
}
