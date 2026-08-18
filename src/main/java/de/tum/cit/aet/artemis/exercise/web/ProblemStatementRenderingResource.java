package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequestDTO;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.ResultSummaryInputDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.exercise.service.ProblemStatementRenderingService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ProblemStatementRenderingResource {

    private static final Logger log = LoggerFactory.getLogger(ProblemStatementRenderingResource.class);

    private final ProblemStatementRenderingService renderingService;

    private final int maxTestResults;

    public ProblemStatementRenderingResource(ProblemStatementRenderingService renderingService,
            @Value("${artemis.problem-statement-rendering.max-test-results:1000}") int maxTestResults) {
        // A negative limit would reject every request carrying test results, including an empty list, with 422. That
        // is silent from the outside: the endpoint keeps answering, just never with a rendering. Refusing to start is
        // the honest response to a limit that cannot be satisfied.
        if (maxTestResults < 0) {
            throw new IllegalArgumentException("artemis.problem-statement-rendering.max-test-results must not be negative, but was " + maxTestResults);
        }
        this.renderingService = renderingService;
        this.maxTestResults = maxTestResults;
    }

    /**
     * POST problem-statement/render : Stateless rendering of a problem statement.
     * <p>
     * The client sends markdown + optional test data, the server returns a self-contained HTML document.
     * The {@code inlineImages} field controls how embedded images are delivered:
     * if {@code true}, images are embedded as Base64 data URIs (self-contained, no auth needed);
     * if {@code false} or omitted (default), images stay as absolute URLs requiring authentication.
     *
     * @param renderRequest the render request containing markdown, test results, and configuration
     * @return the rendered problem statement DTO
     */
    @PostMapping(value = "problem-statement/render", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    @LimitRequestsPerMinute(type = RateLimitType.PROBLEM_STATEMENT_RENDERING)
    public ResponseEntity<?> renderProblemStatement(@Valid @RequestBody ProblemStatementRenderRequestDTO renderRequest) {

        log.debug("REST request to render problem statement (stateless)");

        Map<Long, TestFeedbackInputDTO> testResults = null;
        if (renderRequest.testResults() != null) {
            if (renderRequest.testResults().size() > maxTestResults) {
                return unprocessable("Too many test results", "testResults contains " + renderRequest.testResults().size() + " entries, the maximum is " + maxTestResults + ".");
            }
            testResults = new HashMap<>();
            for (TestFeedbackInputDTO input : renderRequest.testResults()) {
                if (testResults.containsKey(input.testId())) {
                    return unprocessable("Duplicate test id", "Duplicate test id " + input.testId() + " in testResults. Each test id must appear at most once.");
                }
                // Two distinct test ids may legitimately share a test name (no DB uniqueness constraint on
                // test_name), so a duplicate name is not rejected here. TestFeedbackLookup treats such a name as
                // ambiguous and resolves it to not-executed instead of picking one of the two tests.
                testResults.put(input.testId(), input);
            }
        }

        ResultSummaryInputDTO resultSummary = renderRequest.resultSummary();

        String lang = renderRequest.locale() != null ? renderRequest.locale() : "en";
        Locale locale = Locale.forLanguageTag(lang);

        RenderedProblemStatementDTO result = renderingService.render(renderRequest.markdown(), testResults, resultSummary, locale, renderRequest.darkMode(),
                renderRequest.shouldIncludeJs(), renderRequest.shouldIncludeCss(), renderRequest.shouldInlineImages(), Boolean.TRUE.equals(renderRequest.allTestsPassed()));

        return ResponseEntity.ok().eTag("\"" + result.contentHash() + "\"").body(result);
    }

    private static ResponseEntity<?> unprocessable(String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, detail);
        problem.setTitle(title);
        // The renderer shows a rejected render in place, above the statement it could not replace. Without this flag the
        // client's ErrorHandlerInterceptor would raise a global toast for the same failure, so the user would be told
        // twice, once in a message that names the endpoint's internals. Same contract as AccessForbiddenAlertException:
        // the component handling the error displays the more concrete message itself.
        problem.setProperty("skipAlert", true);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
    }
}
