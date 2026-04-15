package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
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

    public ProblemStatementRenderingResource(ProblemStatementRenderingService renderingService) {
        this.renderingService = renderingService;
    }

    /**
     * POST problem-statement/render : Stateless rendering of a problem statement.
     * The client sends markdown + test data, the server returns self-contained HTML with interactive JS.
     * Zero database access.
     *
     * @param renderRequest the render request containing markdown, test results, and configuration
     * @return the rendered problem statement DTO
     */
    @PostMapping(value = "problem-statement/render", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<RenderedProblemStatementDTO> renderProblemStatement(@Valid @RequestBody ProblemStatementRenderRequestDTO renderRequest) {

        log.debug("REST request to render problem statement (stateless)");

        Map<Long, TestFeedbackInputDTO> testResults = null;
        if (renderRequest.testResults() != null && !renderRequest.testResults().isEmpty()) {
            testResults = new HashMap<>();
            for (TestFeedbackInputDTO input : renderRequest.testResults()) {
                if (testResults.containsKey(input.testId())) {
                    return ResponseEntity.badRequest().build();
                }
                testResults.put(input.testId(), input);
            }
        }

        ResultSummaryInputDTO resultSummary = renderRequest.resultSummary();

        String lang = renderRequest.locale() != null ? renderRequest.locale() : "en";
        Locale locale = Locale.forLanguageTag(lang);

        RenderedProblemStatementDTO result = renderingService.render(renderRequest.markdown(), testResults, resultSummary, locale, renderRequest.darkMode(),
                renderRequest.includeJs(), renderRequest.shouldIncludeCss());

        return ResponseEntity.ok().eTag("\"" + result.contentHash() + "\"").body(result);
    }
}
