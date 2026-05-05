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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.exercise.dto.ImageMode;
import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequestDTO;
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

    private final ObjectMapper objectMapper;

    public ProblemStatementRenderingResource(ProblemStatementRenderingService renderingService, ObjectMapper objectMapper) {
        this.renderingService = renderingService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST problem-statement/render : Stateless rendering of a problem statement.
     * The client sends markdown + test data, the server returns self-contained HTML with interactive JS.
     * Zero database access.
     *
     * @param renderRequest the render request containing markdown, test results, and configuration
     * @return the rendered problem statement DTO
     */
    @PostMapping(value = "problem-statement/render", produces = { MediaType.APPLICATION_JSON_VALUE, "multipart/related" })
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    @LimitRequestsPerMinute(type = RateLimitType.PROBLEM_STATEMENT_RENDERING)
    public ResponseEntity<?> renderProblemStatement(@Valid @RequestBody ProblemStatementRenderRequestDTO renderRequest) throws JsonProcessingException {

        log.debug("REST request to render problem statement (stateless)");

        Map<Long, TestFeedbackInputDTO> testResults = null;
        if (renderRequest.testResults() != null && !renderRequest.testResults().isEmpty()) {
            testResults = new HashMap<>();
            for (TestFeedbackInputDTO input : renderRequest.testResults()) {
                if (testResults.containsKey(input.testId())) {
                    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                            "Duplicate test id " + input.testId() + " in testResults. Each test id must appear at most once.");
                    problem.setTitle("Duplicate test id");
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
                }
                testResults.put(input.testId(), input);
            }
        }

        ResultSummaryInputDTO resultSummary = renderRequest.resultSummary();

        String lang = renderRequest.locale() != null ? renderRequest.locale() : "en";
        Locale locale = Locale.forLanguageTag(lang);

        ImageMode imageMode = renderRequest.resolvedImageMode();
        ProblemStatementRenderingService.RenderResult result = renderingService.render(renderRequest.markdown(), testResults, resultSummary, locale, renderRequest.darkMode(),
                renderRequest.shouldIncludeJs(), renderRequest.shouldIncludeCss(), imageMode);

        if (imageMode == ImageMode.ATTACHED) {
            String boundary = "artemis-psr-" + result.dto().contentHash().substring(0, 16);
            byte[] jsonPart = objectMapper.writeValueAsBytes(result.dto());

            StreamingResponseBody body = outputStream -> MultipartRelatedResponseWriter.write(outputStream, boundary, jsonPart, result.attachedImages());

            return ResponseEntity.ok().eTag("\"" + result.dto().contentHash() + "\"")
                    .contentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary + "; type=\"application/json\"; start=\"<root@artemis>\"")).body(body);
        }

        return ResponseEntity.ok().eTag("\"" + result.dto().contentHash() + "\"").body(result.dto());
    }
}
