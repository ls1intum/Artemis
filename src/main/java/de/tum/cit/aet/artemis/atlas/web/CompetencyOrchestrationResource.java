package de.tum.cit.aet.artemis.atlas.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyOrchestrationService;
import de.tum.cit.aet.artemis.core.config.LLMModelCostConfiguration;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;

/**
 * REST controller for the autonomous competency management orchestrator.
 * <p>
 * Availability is gated by the Atlas module ({@link AtlasEnabled}) plus the runtime
 * {@link Feature#AtlasAgent} feature toggle — the same toggle that controls the Atlas Companion
 * chat agent. No separate orchestrator toggle exists.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@RestController
@RequestMapping("api/atlas/orchestrator/")
public class CompetencyOrchestrationResource {

    private static final Logger log = LoggerFactory.getLogger(CompetencyOrchestrationResource.class);

    private final CompetencyOrchestrationService competencyOrchestrationService;

    // TODO TEMPORARY (debug): remove together with the debug endpoints below.
    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    private final LLMTokenUsageService llmTokenUsageService;

    private final LLMModelCostConfiguration llmModelCostConfiguration;

    public CompetencyOrchestrationResource(CompetencyOrchestrationService competencyOrchestrationService, LLMTokenUsageTraceRepository llmTokenUsageTraceRepository,
            LLMTokenUsageService llmTokenUsageService, LLMModelCostConfiguration llmModelCostConfiguration) {
        this.competencyOrchestrationService = competencyOrchestrationService;
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
        this.llmTokenUsageService = llmTokenUsageService;
        this.llmModelCostConfiguration = llmModelCostConfiguration;
    }

    /**
     * TEMPORARY DEBUG endpoint — performs a LIVE cost lookup against the currently running app's loaded
     * config (not the stored rows), so we can tell whether the deployed config can resolve a non-zero
     * cost for a given model string. Distinguishes a stale-rows problem from a config-override problem.
     * Remove once the cost-is-zero issue is diagnosed.
     *
     * @param courseId only used for the instructor authorization check; cost config is global
     * @param model    the model string to resolve (default: the orchestrator's date-suffixed model)
     */
    @GetMapping("debug/courses/{courseId}/cost-config")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<CostConfigDebugDTO> debugCostConfig(@PathVariable Long courseId, @RequestParam(defaultValue = "gpt-5.4-2026-03-05") String model) {
        log.info("REST request (DEBUG) for live LLM cost config lookup of model '{}' (course {})", model, courseId);
        LLMRequest resolved = llmTokenUsageService.buildLLMRequest(model, 1, 1, "DEBUG_LOOKUP");
        CostConfigDebugDTO body = new CostConfigDebugDTO(List.copyOf(llmModelCostConfiguration.getModelCosts().keySet()), model, resolved.costPerMillionInputToken(),
                resolved.costPerMillionOutputToken());
        return ResponseEntity.ok(body);
    }

    /** TEMPORARY DEBUG DTO — remove with the debug endpoints above. */
    public record CostConfigDebugDTO(List<String> configuredKeys, String queriedModel, float resolvedInputCostPerMillion, float resolvedOutputCostPerMillion) {
    }

    /**
     * TEMPORARY DEBUG endpoint — surfaces the raw recorded LLM token-usage rows for a course so the
     * exact model string Azure returned and the resolved cost rates can be inspected from the browser.
     * Remove once the cost-is-zero issue is diagnosed.
     */
    @GetMapping("debug/courses/{courseId}/token-usage")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<TokenUsageDebugDTO>> debugTokenUsage(@PathVariable Long courseId) {
        log.info("REST request (DEBUG) for recorded LLM token usage of course: {}", courseId);
        List<TokenUsageDebugDTO> rows = llmTokenUsageTraceRepository.findAllWithRequestsByCourseId(courseId).stream()
                .flatMap(trace -> trace.getLLMRequests().stream()
                        .map(request -> new TokenUsageDebugDTO(String.valueOf(trace.getServiceType()), request.getModel(), request.getServicePipelineId(),
                                request.getNumInputTokens(), request.getNumOutputTokens(), request.getCostPerMillionInputTokens(), request.getCostPerMillionOutputTokens(),
                                request.getNumInputTokens() * (double) request.getCostPerMillionInputTokens() / 1_000_000
                                        + request.getNumOutputTokens() * (double) request.getCostPerMillionOutputTokens() / 1_000_000)))
                .toList();
        return ResponseEntity.ok(rows);
    }

    /** TEMPORARY DEBUG DTO — remove with the debug endpoint above. */
    public record TokenUsageDebugDTO(String serviceType, String model, String pipelineId, int numInputTokens, int numOutputTokens, float costPerMillionInputTokens,
            float costPerMillionOutputTokens, double computedCostEur) {
    }

    @PostMapping("programming-exercises/{exerciseId}/run")
    @EnforceAtLeastInstructorInExercise
    @FeatureToggle(Feature.AtlasAgent)
    public ResponseEntity<CompetencyOrchestrationResultDTO> runForProgrammingExercise(@PathVariable Long exerciseId) {
        log.info("REST request to run Atlas orchestrator for programming exercise: {}", exerciseId);
        CompetencyOrchestrationResultDTO result = competencyOrchestrationService.run(exerciseId);
        return ResponseEntity.status(httpStatusFor(result)).body(result);
    }

    /** Maps orchestration outcome to HTTP status so frontend error handling does not need to parse the response body. */
    private static HttpStatus httpStatusFor(CompetencyOrchestrationResultDTO result) {
        return switch (result.status()) {
            case SUCCESS -> HttpStatus.OK;
            case PARTIAL -> HttpStatus.MULTI_STATUS;
            case IN_PROGRESS -> HttpStatus.CONFLICT;
            case FAILED -> switch (result.failureReason()) {
                case NO_CHAT_CLIENT -> HttpStatus.SERVICE_UNAVAILABLE;
                case LLM_ERROR -> HttpStatus.BAD_GATEWAY;
                case UNSUPPORTED_EXERCISE -> HttpStatus.UNPROCESSABLE_CONTENT;
            };
        };
    }
}
