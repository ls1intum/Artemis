package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body of the stateless problem-statement rendering endpoint.
 * <p>
 * {@code allTestsPassed} carries the "the result is successful but carries no per-test feedback at all" signal, which
 * cannot be expressed through {@code testResults}. It is only honoured when {@code testResults} is {@code null}:
 * individual test outcomes always win over the flag.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProblemStatementRenderRequestDTO(@NotNull @Size(max = 100_000) @Pattern(regexp = "^[^\u0000]*$", message = "markdown must not contain null bytes") String markdown,
        @Nullable List<@NotNull @Valid TestFeedbackInputDTO> testResults, @Nullable @Valid ResultSummaryInputDTO resultSummary, @Nullable @Size(max = 10) String locale,
        boolean darkMode, @Nullable Boolean includeJs, @Nullable Boolean includeCss, @Nullable Boolean inlineImages, @Nullable Boolean allTestsPassed) {

    /**
     * Compatibility constructor for requests without the {@code allTestsPassed} signal, which is also the wire default:
     * a client that does not know the flag simply never sends it.
     *
     * @param markdown      the raw problem statement markdown
     * @param testResults   the per-test feedback, or {@code null} if no result exists at all
     * @param resultSummary the submission summary, or {@code null}
     * @param locale        the locale tag for user-visible text
     * @param darkMode      whether to render in dark mode
     * @param includeJs     whether to include the interactive feedback modal JS
     * @param includeCss    whether to include embedded CSS
     * @param inlineImages  whether to inline images as Base64 data URIs
     */
    public ProblemStatementRenderRequestDTO(String markdown, @Nullable List<TestFeedbackInputDTO> testResults, @Nullable ResultSummaryInputDTO resultSummary,
            @Nullable String locale, boolean darkMode, @Nullable Boolean includeJs, @Nullable Boolean includeCss, @Nullable Boolean inlineImages) {
        this(markdown, testResults, resultSummary, locale, darkMode, includeJs, includeCss, inlineImages, null);
    }

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
