package de.tum.cit.aet.artemis.hyperion.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.QualityIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.BloomRadarDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QualityIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QualityIssueLocationDTO;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service for analyzing the instructor checklist for programming exercises.
 * Identifies quality issues in the problem statement using AI analysis.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionChecklistService {

    private static final Logger log = LoggerFactory.getLogger(HyperionChecklistService.class);

    private static final String AI_SPAN_KEY = "ai.span";

    private static final String AI_SPAN_VALUE = "true";

    private static final String LF_SPAN_NAME_KEY = "lf.span.name";

    private static final int MAX_CONTEXT_KEY_LENGTH = 100;

    private static final int MAX_CONTEXT_VALUE_LENGTH = 10000;

    /** Maximum timeout for LLM analysis. */
    private static final Duration ANALYSIS_TIMEOUT = Duration.ofSeconds(60);

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final ObservationRegistry observationRegistry;

    public HyperionChecklistService(ChatClient chatClient, HyperionPromptTemplateService templates, ObservationRegistry observationRegistry) {
        this.chatClient = chatClient;
        this.templates = templates;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Analyzes the checklist for a programming exercise problem statement.
     * Runs quality analysis to detect issues in the problem statement.
     * Returns a {@link CompletableFuture} so that the servlet thread is not blocked while waiting for the LLM response.
     *
     * @param request  The request containing the problem statement and metadata
     * @param courseId The ID of the course
     * @return a future that completes with the analysis response containing quality issues
     */
    public CompletableFuture<ChecklistAnalysisResponseDTO> analyzeChecklist(ChecklistAnalysisRequestDTO request, long courseId) {
        log.debug("Performing checklist analysis (exerciseId={})", request.exerciseId());

        Observation observation = Observation.createNotStarted("hyperion.checklist", observationRegistry).contextualName("checklist analysis")
                .lowCardinalityKeyValue(KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE)).start();

        Observation parentObs;
        try (var scope = observation.openScope()) {
            parentObs = observationRegistry.getCurrentObservation();
        }
        catch (Exception e) {
            observation.error(e);
            observation.stop();
            return CompletableFuture.completedFuture(ChecklistAnalysisResponseDTO.empty());
        }

        String problemStatement = request.problemStatementMarkdown();
        var input = Map.of("problem_statement", problemStatement);

        return Mono.fromCallable(() -> runQualityAnalysis(input, parentObs)).subscribeOn(Schedulers.boundedElastic()).timeout(ANALYSIS_TIMEOUT)
                .map(issues -> new ChecklistAnalysisResponseDTO(BloomRadarDTO.empty(), issues)).onErrorResume(e -> {
                    log.warn("Checklist analysis timed out or failed (exerciseId={})", request.exerciseId(), e);
                    observation.error(e);
                    return Mono.just(ChecklistAnalysisResponseDTO.empty());
                }).doFinally(signal -> observation.stop()).toFuture();
    }

    /**
     * Analyzes a single section of the checklist for a programming exercise.
     * Returns a {@link CompletableFuture} so that the servlet thread is not blocked while waiting for the LLM response.
     *
     * @param request  The request containing the problem statement and metadata
     * @param courseId The ID of the course
     * @return a future that completes with the analysis response with only quality issues populated
     */
    public CompletableFuture<ChecklistAnalysisResponseDTO> analyzeSection(ChecklistAnalysisRequestDTO request, long courseId) {
        log.debug("Performing single-section checklist analysis: QUALITY (exerciseId={})", request.exerciseId());

        Observation observation = Observation.createNotStarted("hyperion.checklist.section", observationRegistry).contextualName("checklist section analysis")
                .lowCardinalityKeyValue(KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE)).start();

        Observation parentObs;
        try (var scope = observation.openScope()) {
            parentObs = observationRegistry.getCurrentObservation();
        }
        catch (Exception e) {
            observation.error(e);
            observation.stop();
            return CompletableFuture.completedFuture(ChecklistAnalysisResponseDTO.empty());
        }

        String problemStatement = request.problemStatementMarkdown();
        var input = Map.of("problem_statement", problemStatement);
        final Observation capturedParentObs = parentObs;

        return Mono.fromCallable(() -> {
            List<QualityIssueDTO> issues = runQualityAnalysis(input, capturedParentObs);
            return new ChecklistAnalysisResponseDTO(null, issues);
        }).subscribeOn(Schedulers.boundedElastic()).timeout(ANALYSIS_TIMEOUT).onErrorResume(e -> {
            log.warn("Section analysis timed out or failed: QUALITY (exerciseId={})", request.exerciseId(), e);
            observation.error(e);
            return Mono.just(ChecklistAnalysisResponseDTO.empty());
        }).doFinally(signal -> observation.stop()).toFuture();
    }

    /**
     * Applies a checklist action to modify the problem statement using AI.
     * Builds action-specific instructions and calls the LLM to produce an updated problem statement.
     * Returns a {@link CompletableFuture} so that the servlet thread is not blocked while waiting for the LLM response.
     *
     * @param request the action request containing the action type, problem statement, and context
     * @return a future that completes with the response containing the updated problem statement
     */
    public CompletableFuture<ChecklistActionResponseDTO> applyChecklistAction(ChecklistActionRequestDTO request) {
        log.debug("Applying checklist action: {}", request.actionType());

        Observation observation = Observation.createNotStarted("hyperion.checklist.action", observationRegistry).contextualName("checklist action")
                .lowCardinalityKeyValue(KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE)).start();

        return Mono.fromCallable(() -> {
            // Sanitize context once and reuse for both instructions and summary
            Map<String, String> sanitizedContext = sanitizeContext(request.context());

            String instructions = buildActionInstructions(request.actionType(), sanitizedContext);
            var templateInput = Map.of("action_type", request.actionType().name(), "instructions", instructions, "problem_statement", request.problemStatementMarkdown());

            String renderedPrompt = templates.render("/prompts/hyperion/checklist_action.st", templateInput);

            try {
                String result = chatClient.prompt()
                        .system("You are an expert instructor modifying a programming exercise problem statement. Return ONLY the complete updated problem statement in Markdown.")
                        .user(renderedPrompt).call().content();

                if (result == null || result.isBlank()) {
                    return ChecklistActionResponseDTO.failed(request.problemStatementMarkdown());
                }

                String trimmed = result.trim();
                boolean changed = !trimmed.equals(request.problemStatementMarkdown().trim());
                String summary = buildActionSummary(request.actionType(), sanitizedContext);
                return new ChecklistActionResponseDTO(trimmed, changed, summary);
            }
            catch (Exception e) {
                log.warn("Failed to apply checklist action {}: {}", request.actionType(), e.getMessage(), e);
                observation.error(e);
                return ChecklistActionResponseDTO.failed(request.problemStatementMarkdown());
            }
        }).subscribeOn(Schedulers.boundedElastic()).doOnError(observation::error).doFinally(signal -> observation.stop()).toFuture();
    }

    /** Builds action-specific instructions for the AI prompt based on action type and pre-sanitized context. */
    private String buildActionInstructions(ChecklistActionRequestDTO.ActionType actionType, Map<String, String> ctx) {
        return switch (actionType) {
            case FIX_QUALITY_ISSUE -> {
                String description = ctx.getOrDefault("issueDescription", "Unknown issue");
                String suggestedFix = ctx.getOrDefault("suggestedFix", "");
                String category = ctx.getOrDefault("category", "");
                yield "Fix the following quality issue in the problem statement:\n" + "Category: " + wrapUserValue(category) + "\n" + "Issue: " + wrapUserValue(description) + "\n"
                        + (suggestedFix.isEmpty() ? "" : "Suggested fix: " + wrapUserValue(suggestedFix) + "\n")
                        + "Make minimal, targeted changes to address ONLY this specific issue.";
            }
            case FIX_ALL_QUALITY_ISSUES -> {
                String issuesList = ctx.getOrDefault("allIssues", "No issues provided");
                yield "Fix ALL of the following quality issues in the problem statement:\n" + wrapUserValue(issuesList) + "\n"
                        + "Address each issue with targeted changes. Do not rewrite unrelated sections.";
            }
        };
    }

    /**
     * Wraps a user-supplied context value with triple-backtick fences to reduce prompt injection risk.
     * Returns a placeholder for missing values so the surrounding label is never left bare.
     */
    private static String wrapUserValue(String value) {
        if (value == null || value.isBlank()) {
            return "(not provided)";
        }
        // Escape any triple-backtick sequences to prevent breakout from the fence
        String sanitized = value.replace("```", "``\\`");
        return "\n```user-input\n" + sanitized + "\n```\n";
    }

    /**
     * Builds a short human-readable summary of what action was applied.
     */
    private String buildActionSummary(ChecklistActionRequestDTO.ActionType actionType, Map<String, String> ctx) {
        return switch (actionType) {
            case FIX_QUALITY_ISSUE -> "Fixed quality issue: " + ctx.getOrDefault("category", "unknown");
            case FIX_ALL_QUALITY_ISSUES -> "Fixed all quality issues";
        };
    }

    /**
     * Runs quality analysis.
     */
    private List<QualityIssueDTO> runQualityAnalysis(Map<String, String> input, Observation parentObs) {
        String renderedPrompt = templates.render("/prompts/hyperion/checklist_quality.st", input);

        return runWithObservation("hyperion.checklist.quality", "quality check", parentObs, () -> {
            var response = chatClient.prompt()
                    .system("You are a strict, conservative technical reviewer. Report ONLY issues that clearly match the defined criteria. "
                            + "When in doubt, do NOT report an issue. An empty result is perfectly valid. Return only JSON matching the schema.")
                    .user(renderedPrompt).call().responseEntity(StructuredOutputSchema.QualityResponse.class);

            var entity = response.entity();
            if (entity == null || entity.issues() == null) {
                return List.of();
            }

            return entity.issues().stream().map(this::mapQualityIssueToDto).toList();
        }, List.of());
    }

    private QualityIssueDTO mapQualityIssueToDto(StructuredOutputSchema.QualityIssue issue) {
        var location = issue.location() != null ? new QualityIssueLocationDTO(issue.location().startLine(), issue.location().endLine()) : null;
        return new QualityIssueDTO(parseEnumSafe(QualityIssueCategory.class, issue.category()), parseEnumSafe(Severity.class, issue.severity()), issue.description(), location,
                issue.suggestedFix(), issue.impactOnLearners());
    }

    /**
     * Sanitizes context map entries by truncating keys to {@value MAX_CONTEXT_KEY_LENGTH} characters and values to {@value MAX_CONTEXT_VALUE_LENGTH} characters.
     */
    private Map<String, String> sanitizeContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        var result = new HashMap<String, String>();
        for (var entry : context.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            key = key.length() > MAX_CONTEXT_KEY_LENGTH ? key.substring(0, MAX_CONTEXT_KEY_LENGTH) : key;
            String value = entry.getValue();
            result.put(key, value == null ? "" : (value.length() > MAX_CONTEXT_VALUE_LENGTH ? value.substring(0, MAX_CONTEXT_VALUE_LENGTH) : value));
        }
        return result;
    }

    /**
     * Safely parses a string to an enum constant, returning {@code null} if unrecognized.
     */
    private <E extends Enum<E>> E parseEnumSafe(Class<E> enumType, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            log.debug("Unknown {} value: {}", enumType.getSimpleName(), value);
            return null;
        }
    }

    /**
     * A supplier that may throw a checked exception.
     */
    @FunctionalInterface
    private interface CheckedSupplier<T> {

        T get() throws Exception;
    }

    /**
     * Runs a unit of work inside a child observation span. On success the result is returned;
     * on failure the error is recorded on the span and the {@code fallback} value is returned.
     *
     * @param <T>         the result type
     * @param name        the metric/span name
     * @param contextName the human-readable context name
     * @param parentObs   the parent observation (may be {@code null})
     * @param work        the work to execute inside the observation scope
     * @param fallback    the value returned when {@code work} throws
     * @return the result of {@code work}, or {@code fallback} on error
     */
    private <T> T runWithObservation(String name, String contextName, Observation parentObs, CheckedSupplier<T> work, T fallback) {
        var child = Observation.createNotStarted(name, observationRegistry).contextualName(contextName).lowCardinalityKeyValue(KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(KeyValue.of(LF_SPAN_NAME_KEY, contextName)).parentObservation(parentObs).start();
        try (var scope = child.openScope()) {
            return work.get();
        }
        catch (Exception e) {
            child.error(e);
            log.warn("Failed to run {}", contextName, e);
            return fallback;
        }
        finally {
            child.stop();
        }
    }

    private static class StructuredOutputSchema {

        record QualityResponse(List<QualityIssue> issues) {
        }

        record QualityIssue(String category, String severity, String description, LocationRef location, String suggestedFix, String impactOnLearners) {
        }

        record LocationRef(Integer startLine, Integer endLine) {
        }
    }
}
