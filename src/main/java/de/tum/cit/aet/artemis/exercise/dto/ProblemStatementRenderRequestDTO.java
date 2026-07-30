package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

// NON_NULL, not NON_EMPTY: an explicitly empty testResults list ("result present, nothing mappable") must survive
// re-serialization distinctly from an absent/null list ("no result at all"). NON_EMPTY would drop the empty list
// from the wire and collapse both states.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemStatementRenderRequestDTO(@NotNull @Size(max = 100_000) @Pattern(regexp = "^[^\u0000]*$", message = "markdown must not contain null bytes") String markdown,
        @Nullable List<@NotNull @Valid TestFeedbackInputDTO> testResults, @Nullable @Valid ResultSummaryInputDTO resultSummary, @Nullable @Size(max = 10) String locale,
        boolean darkMode, @Nullable Boolean includeJs, @Nullable Boolean includeCss, @Nullable Boolean inlineImages) {

    /** Whether to include the interactive feedback modal JS in the response. Defaults to true if not specified. */
    public boolean shouldIncludeJs() {
        return includeJs == null || includeJs;
    }

    /** Whether to include embedded CSS in the response. Defaults to true if not specified. */
    public boolean shouldIncludeCss() {
        return includeCss == null || includeCss;
    }

    /** Whether to inline images as Base64 data URIs. Defaults to false (images stay as URLs). */
    public boolean shouldInlineImages() {
        return Boolean.TRUE.equals(inlineImages);
    }
}
