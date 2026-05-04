package de.tum.cit.aet.artemis.hyperion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.CostsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.TimingDTO;
import de.tum.cit.aet.artemis.hyperion.dto.TokensDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;

/**
 * Service orchestrating AI-assisted consistency analysis for {@link ProgrammingExercise} instances.
 * <p>
 * Flow:
 * <ol>
 * <li>Refetch exercise with template & solution participations.</li>
 * <li>Render textual snapshot (problem statement + repositories) via {@link HyperionProgrammingExerciseContextRendererService} and append existing review-thread context.</li>
 * <li>Execute a single unified consistency prompt via the Spring AI {@link ChatClient}.</li>
 * <li>Parse structured JSON into schema classes, normalize, and expose as DTOs.</li>
 * </ol>
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionConsistencyCheckService {

    private static final Logger log = LoggerFactory.getLogger(HyperionConsistencyCheckService.class);

    private static final String CONSISTENCY_PIPELINE_ID = "HYPERION_CONSISTENCY";

    private static final String AI_SPAN_KEY = "ai.span";

    private static final String AI_SPAN_VALUE = "true";

    private static final String LF_SPAN_NAME_KEY = "lf.span.name";

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final HyperionProgrammingExerciseContextRendererService exerciseContextRenderer;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    private final HyperionReviewCommentContextRendererService reviewCommentContextRenderer;

    private final ObservationRegistry observationRegistry;

    /**
     * Creates the consistency-check orchestration service with all required persistence, prompt, and observability dependencies.
     *
     * @param programmingExerciseRepository repository for loading programming exercises with participations
     * @param chatClient                    configured Spring AI chat client
     * @param templates                     prompt template renderer
     * @param exerciseContextRenderer       renderer for exercise problem/repository context
     * @param reviewCommentContextRenderer  renderer for existing review-thread prompt context
     * @param observationRegistry           Micrometer observation registry
     * @param llmTokenUsageService          service for persisting token usage
     * @param userRepository                repository for resolving current user id
     */
    public HyperionConsistencyCheckService(ProgrammingExerciseRepository programmingExerciseRepository, @Nullable ChatClient chatClient, HyperionPromptTemplateService templates,
            HyperionProgrammingExerciseContextRendererService exerciseContextRenderer, HyperionReviewCommentContextRendererService reviewCommentContextRenderer,
            ObservationRegistry observationRegistry, LLMTokenUsageService llmTokenUsageService, UserRepository userRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient;
        this.templates = templates;
        this.exerciseContextRenderer = exerciseContextRenderer;
        this.reviewCommentContextRenderer = reviewCommentContextRenderer;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Maps AI response usage metadata into an {@link LLMRequest} for token accounting.
     *
     * @param response   raw chat response with model metadata
     * @param pipelineId logical pipeline identifier used for usage persistence
     * @return mapped request, or {@code null} if usage metadata is missing
     */
    private LLMRequest buildRequestFromResponse(ChatResponse response, String pipelineId) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        var usage = response.getMetadata().getUsage();
        return llmTokenUsageService.buildLLMRequest(response.getMetadata().getModel(), usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0, pipelineId);
    }

    /**
     * Execute a unified consistency check with existing review-thread context included.
     * Delegates to {@link #checkConsistency(long, boolean)} with {@code skipThreadContext = false}.
     *
     * @param exerciseId id of the programming exercise to check consistency for
     * @return consistency issues, timing, token usage, and costs.
     */
    @Observed(name = "hyperion.consistency", contextualName = "consistency check", lowCardinalityKeyValues = { AI_SPAN_KEY, AI_SPAN_VALUE })
    public ConsistencyCheckResponseDTO checkConsistency(long exerciseId) {
        return checkConsistency(exerciseId, false);
    }

    /**
     * Execute a single unified consistency check that detects both structural and semantic inconsistencies.
     *
     * @param exerciseId        id of the programming exercise to check consistency for
     * @param skipThreadContext if {@code true}, passes empty thread context to the AI prompt (i.e., no prior findings exist).
     *                              Intended for evaluation scripts that assess consistency check quality without prior thread state.
     * @return consistency issues, timing, token usage, and costs.
     */
    @Observed(name = "hyperion.consistency", contextualName = "consistency check", lowCardinalityKeyValues = { AI_SPAN_KEY, AI_SPAN_VALUE })
    public ConsistencyCheckResponseDTO checkConsistency(long exerciseId, boolean skipThreadContext) {
        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "ConsistencyCheck", "ConsistencyCheck.chatClientNotConfigured");
        }

        log.info("Performing consistency check for exercise {}", exerciseId);

        Instant startTime = Instant.now();

        var exerciseWithParticipations = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        String renderedRepositoryContext = exerciseContextRenderer.renderContext(exerciseWithParticipations);
        String programmingLanguage = exerciseWithParticipations.getProgrammingLanguage() != null ? exerciseWithParticipations.getProgrammingLanguage().name() : "JAVA";
        String existingReviewThreads = skipThreadContext ? "{\"threads\":[]}" : reviewCommentContextRenderer.renderReviewThreads(exerciseId);

        Map<String, String> input = Map.of("rendered_context", renderedRepositoryContext, "programming_language", programmingLanguage, "existing_review_threads",
                existingReviewThreads);

        List<LLMRequest> usageCollector = new CopyOnWriteArrayList<>();

        Observation parentObs = observationRegistry.getCurrentObservation();
        List<ConsistencyIssue> issues = runConsistencyCheck(input, parentObs, usageCollector);
        List<ConsistencyIssueDTO> issueDTOs = issues.stream().map(this::mapConsistencyIssueToDto).toList();

        List<LLMRequest> validRequests = usageCollector.stream().filter(Objects::nonNull).toList();
        if (!validRequests.isEmpty()) {
            Long courseId = exerciseWithParticipations.getCourseViaExerciseGroupOrCourseMember() != null
                    ? exerciseWithParticipations.getCourseViaExerciseGroupOrCourseMember().getId()
                    : null;
            Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
            llmTokenUsageService.saveLLMTokenUsage(validRequests, LLMServiceType.HYPERION,
                    builder -> builder.withCourse(courseId).withExercise(exerciseWithParticipations.getId()).withUser(userId));
        }

        Instant endTime = Instant.now();
        double durationSeconds = Duration.between(startTime, endTime).toMillis() / 1000.0;
        var timingDTO = new TimingDTO(startTime.toString(), endTime.toString(), durationSeconds);

        long totalPromptTokens = validRequests.stream().mapToLong(LLMRequest::numInputTokens).sum();
        long totalCompletionTokens = validRequests.stream().mapToLong(LLMRequest::numOutputTokens).sum();
        double promptCost = validRequests.stream().mapToDouble(r -> r.numInputTokens() * r.costPerMillionInputToken() / 1_000_000.0).sum();
        double completionCost = validRequests.stream().mapToDouble(r -> r.numOutputTokens() * r.costPerMillionOutputToken() / 1_000_000.0).sum();

        var tokenDTO = new TokensDTO(totalPromptTokens, totalCompletionTokens, totalPromptTokens + totalCompletionTokens);
        var costsDto = new CostsDTO(promptCost, completionCost, promptCost + completionCost);

        log.debug("Consistency check for exercise {} complete: {} issues", exerciseId, issueDTOs.size());
        issueDTOs.forEach(issue -> log.debug("Issue [{}] {}: {}", issue.severity(), issue.category(), issue.description()));

        return new ConsistencyCheckResponseDTO(startTime, issueDTOs, timingDTO, tokenDTO, costsDto);
    }

    /**
     * Run the unified consistency prompt covering both structural and semantic issues. Returns empty list on any exception.
     *
     * @param input          prompt variables (rendered_context, programming_language, existing_review_threads)
     * @param parentObs      parent observation for tracing
     * @param usageCollector thread-safe list to collect LLM request data
     * @return consistency issues (never null)
     */
    private List<ConsistencyIssue> runConsistencyCheck(Map<String, String> input, Observation parentObs, List<LLMRequest> usageCollector) {
        var child = Observation.createNotStarted("hyperion.consistency.check", observationRegistry).contextualName("consistency check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "consistency check")).parentObservation(parentObs).start();
        final var resourcePath = "/prompts/hyperion/consistency_semantic.st";
        String renderedPrompt = templates.render(resourcePath, input);
        try (Observation.Scope scope = child.openScope()) {
            // @formatter:off
            var response = chatClient
                .prompt()
                .system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.")
                .user(renderedPrompt)
                .call()
                .responseEntity(StructuredOutputSchema.UnifiedConsistencyIssues.class);
            // @formatter:on
            usageCollector.add(buildRequestFromResponse(response.getResponse(), CONSISTENCY_PIPELINE_ID));
            var entity = response.entity();
            return (entity == null || entity.issues == null) ? List.of() : List.copyOf(entity.issues);
        }
        catch (RuntimeException e) {
            child.error(e);
            log.warn("Failed to obtain or parse AI response for {} - returning empty list", resourcePath, e);
            return new ArrayList<>();
        }
        finally {
            child.stop();
        }
    }

    /**
     * Convert an internal issue record into an outward-facing DTO.
     *
     * @param issue internal unified consistency issue
     * @return DTO for API responses
     */
    private ConsistencyIssueDTO mapConsistencyIssueToDto(ConsistencyIssue issue) {
        Severity severity = switch (issue.severity() == null ? "MEDIUM" : issue.severity().toUpperCase()) {
            case "LOW" -> Severity.LOW;
            case "HIGH" -> Severity.HIGH;
            default -> Severity.MEDIUM;
        };
        List<ArtifactLocationDTO> locations = issue.relatedLocations() == null ? List.of()
                : issue.relatedLocations().stream().filter(Objects::nonNull).map(loc -> new ArtifactLocationDTO(loc.type() == null ? ArtifactType.PROBLEM_STATEMENT : loc.type(),
                        loc.filePath(), loc.startLine(), loc.endLine(), normalizeSuggestedInlineFix(loc))).toList();
        ConsistencyIssueCategory category = issue.category() != null ? issue.category() : ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH;
        return new ConsistencyIssueDTO(severity, category, issue.description(), issue.suggestedFix(), locations);
    }

    @Nullable
    private String normalizeSuggestedInlineFix(StructuredOutputSchema.ArtifactLocation location) {
        if (location.inlineFixOperation() == StructuredOutputSchema.InlineFixOperation.DELETE) {
            return "";
        }
        String suggestedInlineFix = location.suggestedInlineFix();
        return suggestedInlineFix == null || suggestedInlineFix.isBlank() ? null : suggestedInlineFix;
    }

    // Unified consistency issue used internally after parsing
    private record ConsistencyIssue(String severity, ConsistencyIssueCategory category, String description, String suggestedFix,
            List<StructuredOutputSchema.ArtifactLocation> relatedLocations) {
    }

    // TODO: try to use records instead of static classes
    // Structured output schema for parsing AI responses
    private static class StructuredOutputSchema {

        private static class UnifiedConsistencyIssues {

            public List<ConsistencyIssue> issues = List.of();
        }

        private enum InlineFixOperation {
            NONE, REPLACE, DELETE
        }

        private record ArtifactLocation(ArtifactType type, String filePath, Integer startLine, Integer endLine, String suggestedInlineFix, InlineFixOperation inlineFixOperation) {
        }
    }

}
