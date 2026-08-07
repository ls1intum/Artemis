package de.tum.cit.aet.artemis.hyperion.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Finds contradictions between a {@link ProgrammingExercise}'s problem statement and its repositories.
 * <p>
 * A structural and a semantic check run concurrently over the same rendered snapshot of the exercise, then a third pass verifies their combined output: the two checkers are prone
 * to false positives and to reporting the same contradiction twice, and neither can see the other's findings. Every step degrades to its input on failure, so a check reports
 * fewer issues rather than none.
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

    private final ObjectMapper objectMapper;

    public HyperionConsistencyCheckService(ProgrammingExerciseRepository programmingExerciseRepository, @Nullable ChatClient chatClient, HyperionPromptTemplateService templates,
            HyperionProgrammingExerciseContextRendererService exerciseContextRenderer, HyperionReviewCommentContextRendererService reviewCommentContextRenderer,
            ObservationRegistry observationRegistry, LLMTokenUsageService llmTokenUsageService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient;
        this.templates = templates;
        this.exerciseContextRenderer = exerciseContextRenderer;
        this.reviewCommentContextRenderer = reviewCommentContextRenderer;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
        this.observationRegistry = observationRegistry;
        this.objectMapper = objectMapper;
    }

    /** Returns {@code null} for a response whose provider reported no usage; the collector filters those out rather than accounting for zero tokens. */
    private LLMRequest buildRequestFromResponse(ChatResponse response, String pipelineId) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        var usage = response.getMetadata().getUsage();
        return llmTokenUsageService.buildLLMRequest(response.getMetadata().getModel(), usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0, pipelineId, response.getMetadata().getId(), usage.getCacheReadInputTokens());
    }

    /**
     * Checks the exercise, with the findings of earlier checks in context.
     *
     * @param exerciseId the programming exercise to check
     * @return the issues found, with timing, token usage, and costs
     */
    @Observed(name = "hyperion.consistency", contextualName = "consistency check", lowCardinalityKeyValues = { AI_SPAN_KEY, AI_SPAN_VALUE })
    public ConsistencyCheckResponseDTO checkConsistency(long exerciseId) {
        return checkConsistency(exerciseId, false);
    }

    /**
     * Checks the exercise for contradictions between its problem statement and its repositories.
     *
     * @param exerciseId        the programming exercise to check
     * @param skipThreadContext hides the findings of earlier checks from the prompts, so a run can be judged on its own merits
     * @return the issues found, with timing, token usage, and costs
     * @throws InternalServerErrorAlertException if no chat client is configured
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

        // Written from the two concurrent checks below as well as from this thread.
        List<LLMRequest> usageCollector = new CopyOnWriteArrayList<>();

        Observation parentObs = observationRegistry.getCurrentObservation();
        var structuralMono = Mono.fromCallable(() -> runStructuralCheck(input, parentObs, usageCollector)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());
        var semanticMono = Mono.fromCallable(() -> runSemanticCheck(input, parentObs, usageCollector)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());

        var results = Mono.zip(structuralMono, semanticMono).block();
        var structuralIssues = results != null ? results.getT1() : List.<ConsistencyIssue>of();
        var semanticIssues = results != null ? results.getT2() : List.<ConsistencyIssue>of();

        final List<ConsistencyIssue> combinedIssues = Stream.concat(structuralIssues.stream(), semanticIssues.stream()).toList();

        // A verification pass that cannot run must not cost the instructor the findings; unverified issues are better than none.
        List<ConsistencyIssueDTO> issueDTOs;
        try {
            final String issuesJson = objectMapper.writeValueAsString(Map.of("issues", combinedIssues.stream().map(this::mapConsistencyIssueToDto).toList()));
            var verificationInput = new HashMap<>(input);
            verificationInput.put("detected_issues_json", issuesJson);
            List<ConsistencyIssue> verifiedIssues = runVerificationCheck(verificationInput, parentObs, usageCollector);
            issueDTOs = verifiedIssues != null ? verifiedIssues.stream().map(this::mapConsistencyIssueToDto).toList()
                    : combinedIssues.stream().map(this::mapConsistencyIssueToDto).toList();
            log.info("Verification step: {} raw issues -> {} verified issues", combinedIssues.size(), issueDTOs.size());
        }
        catch (Exception e) {
            log.error("Verification step failed — falling back to pre-verification results", e);
            issueDTOs = combinedIssues.stream().map(this::mapConsistencyIssueToDto).toList();
        }

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
        double promptCost = validRequests.stream().mapToDouble(r -> {
            long cachedTokens = Math.min(r.numInputTokens(), r.numCachedInputTokens() == null ? 0 : r.numCachedInputTokens());
            return (r.numInputTokens() - cachedTokens) * r.costPerMillionInputToken() / 1_000_000.0 + cachedTokens * r.costPerMillionCachedInputToken() / 1_000_000.0;
        }).sum();
        double completionCost = validRequests.stream().mapToDouble(r -> r.numOutputTokens() * r.costPerMillionOutputToken() / 1_000_000.0).sum();

        var tokenDTO = new TokensDTO(totalPromptTokens, totalCompletionTokens, totalPromptTokens + totalCompletionTokens);
        var costsDto = new CostsDTO(promptCost, completionCost, promptCost + completionCost);

        log.debug("Consistency check for exercise {} complete: {} issues", exerciseId, issueDTOs.size());
        issueDTOs.forEach(issue -> log.debug("Issue [{}] {}: {}", issue.severity(), issue.category(), issue.description()));

        return new ConsistencyCheckResponseDTO(startTime, issueDTOs, timingDTO, tokenDTO, costsDto);
    }

    private List<ConsistencyIssue> runStructuralCheck(Map<String, String> input, Observation parentObs, List<LLMRequest> usageCollector) {
        var child = Observation.createNotStarted("hyperion.consistency.structural", observationRegistry).contextualName("structural check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "structural check")).parentObservation(parentObs).start();
        final var resourcePath = "/prompts/hyperion/consistency_structural.st";
        String renderedPrompt = templates.render(resourcePath, input);
        try (Observation.Scope scope = child.openScope()) {
            // @formatter:off
            var structuralIssuesResponse = chatClient
                .prompt()
                .system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.")
                .user(renderedPrompt)
                .call()
                .responseEntity(StructuredOutputSchema.StructuralConsistencyIssues.class);
            // @formatter:on
            usageCollector.add(buildRequestFromResponse(structuralIssuesResponse.getResponse(), CONSISTENCY_PIPELINE_ID));
            return toConsistencyIssues(structuralIssuesResponse.entity());
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

    private List<ConsistencyIssue> runSemanticCheck(Map<String, String> input, Observation parentObs, List<LLMRequest> usageCollector) {
        var child = Observation.createNotStarted("hyperion.consistency.semantic", observationRegistry).contextualName("semantic check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "semantic check")).parentObservation(parentObs).start();
        final var resourcePath = "/prompts/hyperion/consistency_semantic.st";
        String renderedPrompt = templates.render(resourcePath, input);
        try (Observation.Scope scope = child.openScope()) {
            // @formatter:off
            var semanticIssuesResponse = chatClient
                .prompt()
                .system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.")
                .user(renderedPrompt)
                .call()
                .responseEntity(StructuredOutputSchema.SemanticConsistencyIssues.class);
            // @formatter:on
            usageCollector.add(buildRequestFromResponse(semanticIssuesResponse.getResponse(), CONSISTENCY_PIPELINE_ID));
            return toConsistencyIssues(semanticIssuesResponse.entity());
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
     * Drops false positives, merges the two checkers' duplicates, and sharpens what survives. Handed no issues, it checks the exercise itself and may report its own.
     * <p>
     * {@code null} distinguishes a failed call, where the caller keeps the unverified issues, from a successful call that rejected every issue.
     */
    private List<ConsistencyIssue> runVerificationCheck(Map<String, String> input, Observation parentObs, List<LLMRequest> usageCollector) {
        var child = Observation.createNotStarted("hyperion.consistency.verification", observationRegistry).contextualName("verification check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "verification check")).parentObservation(parentObs).start();

        final var resourcePath = "/prompts/hyperion/consistency_verification.st";
        final String renderedPrompt = templates.render(resourcePath, input);
        try (Observation.Scope scope = child.openScope()) {
            var verificationResponse = chatClient.prompt().system("You are a senior educational quality assurance engineer. Return only JSON matching the schema.")
                    .user(renderedPrompt).call().responseEntity(StructuredOutputSchema.UnifiedConsistencyIssues.class);

            usageCollector.add(buildRequestFromResponse(verificationResponse.getResponse(), CONSISTENCY_PIPELINE_ID));
            var entity = verificationResponse.entity();
            return (entity == null || entity.issues() == null) ? List.of() : List.copyOf(entity.issues());
        }
        catch (RuntimeException e) {
            child.error(e);
            log.warn("Verification call failed — caller will fall back to pre-verification results", e);
            return null;
        }
        finally {
            child.stop();
        }
    }

    /** Fills in defaults for the fields a model is free to leave out or spell freely, so that a partially answered issue still reaches the instructor. */
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
        // An empty replacement is how a deletion is expressed downstream, and is distinct from null, which means there is no inline fix to apply at all.
        if (location.inlineFixOperation() == StructuredOutputSchema.InlineFixOperation.DELETE) {
            return "";
        }
        String suggestedInlineFix = location.suggestedInlineFix();
        return suggestedInlineFix == null || suggestedInlineFix.isBlank() ? null : suggestedInlineFix;
    }

    private List<ConsistencyIssue> toConsistencyIssues(StructuredOutputSchema.StructuralConsistencyIssues structuralIssues) {
        if (structuralIssues == null || structuralIssues.issues() == null) {
            return List.of();
        }
        return structuralIssues.issues().stream().map(issue -> new ConsistencyIssue(issue.severity(),
                issue.category() != null ? ConsistencyIssueCategory.valueOf(issue.category().name()) : null, issue.description(), issue.suggestedFix(), issue.relatedLocations()))
                .toList();
    }

    private List<ConsistencyIssue> toConsistencyIssues(StructuredOutputSchema.SemanticConsistencyIssues semanticIssues) {
        if (semanticIssues == null || semanticIssues.issues() == null) {
            return List.of();
        }
        return semanticIssues.issues().stream().map(issue -> new ConsistencyIssue(issue.severity(),
                issue.category() != null ? ConsistencyIssueCategory.valueOf(issue.category().name()) : null, issue.description(), issue.suggestedFix(), issue.relatedLocations()))
                .toList();
    }

    /** The internal issue shape, and at the same time the schema the verifier answers in, so a field added here is a field the verifier may fill. */
    private record ConsistencyIssue(String severity, ConsistencyIssueCategory category, String description, String suggestedFix,
            List<StructuredOutputSchema.ArtifactLocation> relatedLocations) {
    }

    /** The response schemas the models are held to. Each checker gets only the categories it is responsible for, so it cannot report outside its remit. */
    private static class StructuredOutputSchema {

        private record StructuralConsistencyIssues(List<StructuralConsistencyIssue> issues) {
        }

        private enum StructuralConsistencyIssueCategory {
            METHOD_RETURN_TYPE_MISMATCH, METHOD_PARAMETER_MISMATCH, CONSTRUCTOR_PARAMETER_MISMATCH, ATTRIBUTE_TYPE_MISMATCH, VISIBILITY_MISMATCH
        }

        private record StructuralConsistencyIssue(String severity, StructuralConsistencyIssueCategory category, String description, String suggestedFix,
                List<ArtifactLocation> relatedLocations) {
        }

        private record SemanticConsistencyIssues(List<SemanticConsistencyIssue> issues) {
        }

        private enum SemanticConsistencyIssueCategory {
            IDENTIFIER_NAMING_INCONSISTENCY
        }

        private record SemanticConsistencyIssue(String severity, SemanticConsistencyIssueCategory category, String description, String suggestedFix,
                List<ArtifactLocation> relatedLocations) {
        }

        /** The verifier judges both checkers' findings, so unlike them it may use any category. */
        private record UnifiedConsistencyIssues(List<ConsistencyIssue> issues) {
        }

        private enum InlineFixOperation {
            NONE, REPLACE, DELETE
        }

        private record ArtifactLocation(ArtifactType type, String filePath, Integer startLine, Integer endLine, String suggestedInlineFix, InlineFixOperation inlineFixOperation) {
        }
    }

}
