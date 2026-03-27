package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProblemStatementRenderRequest(@NotNull @Size(max = 100_000) String markdown, @Nullable @Size(max = 100) List<@NotNull @Valid TestFeedbackInput> testResults,
        @Nullable @Valid ResultSummaryInput resultSummary, @Nullable @Size(max = 10) String locale, boolean darkMode, boolean includeJs, @Nullable Boolean includeCss) {

    /** Whether to include embedded CSS in the response. Defaults to true if not specified. */
    public boolean shouldIncludeCss() {
        return includeCss == null || includeCss;
    }
}
