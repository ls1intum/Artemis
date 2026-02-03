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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service orchestrating AI-assisted structural and semantic consistency analysis for {@link ProgrammingExercise} instances.
 * <p>
 * Flow:
 * <ol>
 * <li>Refetch exercise with template & solution participations.</li>
 * <li>Render textual snapshot (problem statement + repositories) via {@link HyperionProgrammingExerciseContextRendererService}.</li>
 * <li>Execute structural & semantic prompts concurrently using the Spring AI {@link ChatClient}.</li>
 * <li>Parse structured JSON into schema classes, normalize, aggregate, and expose as DTOs.</li>
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

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final HyperionProgrammingExerciseContextRendererService exerciseContextRenderer;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    private final ObservationRegistry observationRegistry;

    public HyperionConsistencyCheckService(ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, HyperionPromptTemplateService templates,
            HyperionProgrammingExerciseContextRendererService exerciseContextRenderer, ObservationRegistry observationRegistry, LLMTokenUsageService llmTokenUsageService,
            UserRepository userRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient;
        this.templates = templates;
        this.exerciseContextRenderer = exerciseContextRenderer;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
        this.observationRegistry = observationRegistry;
    }

    private LLMRequest buildRequestFromResponse(ChatResponse response, String pipelineId) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        var usage = response.getMetadata().getUsage();
        return llmTokenUsageService.buildLLMRequest(response.getMetadata().getModel(), usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0, pipelineId);
    }

    /**
     * Execute structural and semantic consistency checks. Model calls run concurrently on bounded elastic threads.
     * Any individual failure degrades gracefully to an empty list; the aggregated response is always non-null.
     *
     * @param exercise programming exercise reference to check consistency for
     * @return aggregated consistency issues, timing, token usage, and costs.
     */
    @Observed(name = "hyperion.consistency", contextualName = "consistency check", lowCardinalityKeyValues = { AI_SPAN_KEY, AI_SPAN_VALUE })
    public ConsistencyCheckResponseDTO checkConsistency(ProgrammingExercise exercise) {
        log.info("Performing consistency check for exercise {}", exercise.getId());

        Instant startTime = Instant.now();

        var exerciseWithParticipations = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        String renderedRepositoryContext = exerciseContextRenderer.renderContext(exerciseWithParticipations);
        String programmingLanguage = exerciseWithParticipations.getProgrammingLanguage() != null ? exerciseWithParticipations.getProgrammingLanguage().name() : "JAVA";
        var input = Map.of("rendered_context", renderedRepositoryContext, "programming_language", programmingLanguage);

        // Thread-safe collector for usage data from parallel checks
        List<LLMRequest> usageCollector = new CopyOnWriteArrayList<>();

        Observation parentObs = observationRegistry.getCurrentObservation();
        var structuralMono = Mono.fromCallable(() -> runStructuralCheck(input, parentObs, usageCollector)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());
        var semanticMono = Mono.fromCallable(() -> runSemanticCheck(input, parentObs, usageCollector)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());

        var results = Mono.zip(structuralMono, semanticMono).block();
        var structuralIssues = results != null ? results.getT1() : List.<ConsistencyIssue>of();
        var semanticIssues = results != null ? results.getT2() : List.<ConsistencyIssue>of();

        List<ConsistencyIssue> combinedIssues = Stream.concat(structuralIssues.stream(), semanticIssues.stream()).toList();
        List<ConsistencyIssueDTO> issueDTOs = combinedIssues.stream().map(this::mapConsistencyIssueToDto).toList();

        List<ConsistencyIssue> combinedIssuesVerified = new ArrayList<>(combinedIssues);

        try {
            ObjectMapper mapper = new ObjectMapper();
            String issuesJson = mapper.writeValueAsString(combinedIssuesVerified.stream().map(this::mapConsistencyIssueToDto).toList());

            var verificationInput = new HashMap<>(input);
            verificationInput.put("detected_issues_json", issuesJson);

            combinedIssuesVerified = runVerificationCheck(verificationInput, parentObs, usageCollector);

            log.info("Verification reduced issues from {} to {}", combinedIssues.size(), combinedIssuesVerified.size());
        }
        catch (Exception e) {
            log.error("Error during verification step", e);
            combinedIssuesVerified = combinedIssues;
        }

        List<LLMRequest> validRequests = usageCollector.stream().filter(Objects::nonNull).toList();
        if (!validRequests.isEmpty()) {
            Long courseId = exerciseWithParticipations.getCourseViaExerciseGroupOrCourseMember() != null
                    ? exerciseWithParticipations.getCourseViaExerciseGroupOrCourseMember().getId()
                    : null;
            Long userId = SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);
            llmTokenUsageService.saveLLMTokenUsage(validRequests, LLMServiceType.HYPERION,
                    builder -> builder.withCourse(courseId).withExercise(exerciseWithParticipations.getId()).withUser(userId));
        }

        List<ConsistencyIssueDTO> issueVerifiedDTOs = combinedIssuesVerified.stream().map(this::mapConsistencyIssueToDto).toList();

        // Timing
        Instant endTime = Instant.now();
        double durationSeconds = Duration.between(startTime, endTime).toMillis() / 1000.0;
        var timingDTO = new TimingDTO(startTime.toString(), endTime.toString(), durationSeconds);

        // Aggregate token usage and costs from LLMRequest data
        long totalPromptTokens = validRequests.stream().mapToLong(LLMRequest::numInputTokens).sum();
        long totalCompletionTokens = validRequests.stream().mapToLong(LLMRequest::numOutputTokens).sum();
        double promptCost = validRequests.stream().mapToDouble(r -> r.numInputTokens() * r.costPerMillionInputToken() / 1_000_000.0).sum();
        double completionCost = validRequests.stream().mapToDouble(r -> r.numOutputTokens() * r.costPerMillionOutputToken() / 1_000_000.0).sum();

        var tokenDTO = new TokensDTO(totalPromptTokens, totalCompletionTokens, totalPromptTokens + totalCompletionTokens);
        var costsDto = new CostsDTO(promptCost, completionCost, promptCost + completionCost);

        if (issueDTOs.isEmpty()) {
            log.info("No consistency issues found for exercise {}", exercise.getId());
        }
        else {
            log.info("Consistency check for exercise {} found {} issues", exercise.getId(), issueDTOs.size());
            for (var issue : issueDTOs) {
                log.info("Consistency issue for exercise {}: [{}] {} - Suggested fix: {}", exercise.getId(), issue.severity(), issue.description(), issue.suggestedFix());
            }
        }
        return new ConsistencyCheckResponseDTO(startTime, issueDTOs, issueVerifiedDTOs, timingDTO, tokenDTO, costsDto);
    }

    /**
     * Run the structural consistency prompt. Returns empty list on any exception.
     *
     * @param input          prompt variables (rendered_context, programming_language)
     * @param parentObs      parent observation for tracing
     * @param usageCollector thread-safe list to collect LLM request data
     * @return structural issues (never null)
     */
    private List<ConsistencyIssue> runStructuralCheck(Map<String, String> input, Observation parentObs, List<LLMRequest> usageCollector) {
        var child = Observation.createNotStarted("hyperion.consistency.structural", observationRegistry).contextualName("structural check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "structural check")).parentObservation(parentObs).start();
        var resourcePath = "/prompts/hyperion/consistency_structural.st";
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
            return toGenericConsistencyIssue(structuralIssuesResponse.entity());
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
     * Run the semantic consistency prompt. Returns empty list on any exception.
     *
     * @param input          prompt variables (rendered_context, programming_language)
     * @param parentObs      parent observation for tracing
     * @param usageCollector thread-safe list to collect LLM request data
     * @return semantic issues (never null)
     */
    private List<ConsistencyIssue> runSemanticCheck(Map<String, String> input, Observation parentObs, List<LLMRequest> usageCollector) {
        var child = Observation.createNotStarted("hyperion.consistency.semantic", observationRegistry).contextualName("semantic check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "semantic check")).parentObservation(parentObs).start();
        var resourcePath = "/prompts/hyperion/consistency_semantic.st";
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
            return toGenericConsistencyIssue(semanticIssuesResponse.entity());
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
     * Run the verification prompt to filter false positives.
     *
     * @param input          prompt variables (rendered_context, detected_issues_json)
     * @param parentObs      parent observation for tracing
     * @param usageCollector list to collect LLM request data
     * @return filtered list of issues
     */
    private List<ConsistencyIssue> runVerificationCheck(Map<String, String> input, Observation parentObs, List<LLMRequest> usageCollector) {
        var child = Observation.createNotStarted("hyperion.consistency.verification", observationRegistry).contextualName("verification check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of(AI_SPAN_KEY, AI_SPAN_VALUE))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of(LF_SPAN_NAME_KEY, "verification check")).parentObservation(parentObs).start();

        var resourcePath = "/prompts/hyperion/consistency_verification.st";
        String renderedPrompt = templates.render(resourcePath, input);
        try (Observation.Scope scope = child.openScope()) {
            var verificationResponse = chatClient.prompt().system("You are a quality assurance verifier. Filter the provided issues and return only valid ones.")
                    .user(renderedPrompt).call().responseEntity(StructuredOutputSchema.StructuralConsistencyIssues.class);

            usageCollector.add(buildRequestFromResponse(verificationResponse.getResponse(), CONSISTENCY_PIPELINE_ID));
            return toGenericConsistencyIssue(verificationResponse.entity());
        }
        catch (RuntimeException e) {
            child.error(e);
            log.warn("Failed to verification AI response - returning original list as fallback", e);
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
                : issue.relatedLocations().stream()
                        .map(loc -> new ArtifactLocationDTO(loc.type() == null ? ArtifactType.PROBLEM_STATEMENT : loc.type(), loc.filePath(), loc.startLine(), loc.endLine()))
                        .toList();
        ConsistencyIssueCategory category = issue.category() != null ? issue.category() : ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH;
        return new ConsistencyIssueDTO(severity, category, issue.description(), issue.suggestedFix(), locations);
    }

    /**
     * Normalize structural issue structured output schema to internal issue representations.
     *
     * @param structuralIssues parsed structural model output
     * @return immutable list of issues
     */
    private List<ConsistencyIssue> toGenericConsistencyIssue(StructuredOutputSchema.StructuralConsistencyIssues structuralIssues) {
        if (structuralIssues == null || structuralIssues.issues == null) {
            return List.of();
        }
        return structuralIssues.issues.stream().map(issue -> new ConsistencyIssue(issue.severity(),
                issue.category() != null ? ConsistencyIssueCategory.valueOf(issue.category().name()) : null, issue.description(), issue.suggestedFix(), issue.relatedLocations()))
                .toList();
    }

    /**
     * Normalize semantic issue structured output schema to internal issue representations.
     *
     * @param semanticIssues parsed semantic model output
     * @return immutable list of issues
     */
    private List<ConsistencyIssue> toGenericConsistencyIssue(StructuredOutputSchema.SemanticConsistencyIssues semanticIssues) {
        if (semanticIssues == null || semanticIssues.issues == null) {
            return List.of();
        }
        return semanticIssues.issues.stream().map(issue -> new ConsistencyIssue(issue.severity(),
                issue.category() != null ? ConsistencyIssueCategory.valueOf(issue.category().name()) : null, issue.description(), issue.suggestedFix(), issue.relatedLocations()))
                .toList();
    }

    // Unified consistency issue used internally after parsing
    private record ConsistencyIssue(String severity, ConsistencyIssueCategory category, String description, String suggestedFix,
            List<StructuredOutputSchema.ArtifactLocation> relatedLocations) {
    }

    // TODO: try to use records instead of static classes
    // Grouped structured output schema for parsing AI responses
    private static class StructuredOutputSchema {

        private static class StructuralConsistencyIssues {

            public List<StructuralConsistencyIssue> issues = List.of();
        }

        private enum StructuralConsistencyIssueCategory {
            METHOD_RETURN_TYPE_MISMATCH, METHOD_PARAMETER_MISMATCH, CONSTRUCTOR_PARAMETER_MISMATCH, ATTRIBUTE_TYPE_MISMATCH, VISIBILITY_MISMATCH
        }

        private record StructuralConsistencyIssue(String severity, StructuralConsistencyIssueCategory category, String description, String suggestedFix,
                List<ArtifactLocation> relatedLocations) {
        }

        private static class SemanticConsistencyIssues {

            public List<SemanticConsistencyIssue> issues = List.of();
        }

        private enum SemanticConsistencyIssueCategory {
            IDENTIFIER_NAMING_INCONSISTENCY
        }

        private record SemanticConsistencyIssue(String severity, SemanticConsistencyIssueCategory category, String description, String suggestedFix,
                List<ArtifactLocation> relatedLocations) {
        }

        private record ArtifactLocation(ArtifactType type, String filePath, Integer startLine, Integer endLine) {
        }
    }
}
