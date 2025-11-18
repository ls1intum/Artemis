package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.Severity;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import reactor.core.publisher.Flux;
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

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final HyperionProgrammingExerciseContextRendererService exerciseContextRenderer;

    private final ObservationRegistry observationRegistry;

    public HyperionConsistencyCheckService(ProgrammingExerciseRepository programmingExerciseRepository, ChatClient chatClient, HyperionPromptTemplateService templates,
            HyperionProgrammingExerciseContextRendererService exerciseContextRenderer, ObservationRegistry observationRegistry) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient;
        this.templates = templates;
        this.exerciseContextRenderer = exerciseContextRenderer;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Execute structural and semantic consistency checks. Model calls run concurrently on bounded elastic threads.
     * Any individual failure degrades gracefully to an empty list; the aggregated response is always non-null.
     *
     * @param exercise programming exercise reference to check consistency for
     * @return aggregated consistency issues
     */
    @Observed(name = "hyperion.consistency", contextualName = "consistency check", lowCardinalityKeyValues = { "ai.span", "true" })
    public ConsistencyCheckResponseDTO checkConsistency(ProgrammingExercise exercise) {
        log.info("Performing consistency check for exercise {}", exercise.getId());
        var exerciseWithParticipations = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        String renderedRepositoryContext = exerciseContextRenderer.renderContext(exerciseWithParticipations);
        String programmingLanguage = exerciseWithParticipations.getProgrammingLanguage() != null ? exerciseWithParticipations.getProgrammingLanguage().name() : "JAVA";
        var input = Map.of("rendered_context", renderedRepositoryContext, "programming_language", programmingLanguage);

        Observation parentObs = observationRegistry.getCurrentObservation();
        var structuralMono = Mono.fromCallable(() -> runStructuralCheck(input, parentObs)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());
        var semanticMono = Mono.fromCallable(() -> runSemanticCheck(input, parentObs)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());

        // @formatter:off
        List<ConsistencyIssue> combinedIssues = Flux.merge(
            structuralMono.flatMapMany(Flux::fromIterable),
            semanticMono.flatMapMany(Flux::fromIterable)
        ).collectList().block();
        // @formatter:on

        List<ConsistencyIssueDTO> issueDTOs = Objects.requireNonNullElse(combinedIssues, new ArrayList<ConsistencyIssue>()).stream().map(this::mapConsistencyIssueToDto).toList();
        if (issueDTOs.isEmpty()) {
            log.info("No consistency issues found for exercise {}", exercise.getId());
        }
        else {
            log.info("Consistency check for exercise {} found {} issues", exercise.getId(), issueDTOs.size());
            for (var issue : issueDTOs) {
                log.info("Consistency issue for exercise {}: [{}] {} - Suggested fix: {}", exercise.getId(), issue.severity(), issue.description(), issue.suggestedFix());
            }
        }
        return new ConsistencyCheckResponseDTO(issueDTOs);
    }

    /**
     * Run the structural consistency prompt. Returns empty list on any exception.
     *
     * @param input prompt variables (rendered_context, programming_language)
     * @return structural issues (never null)
     */
    private List<ConsistencyIssue> runStructuralCheck(Map<String, String> input, Observation parentObs) {
        var child = Observation.createNotStarted("hyperion.consistency.structural", observationRegistry).contextualName("structural check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of("ai.span", "true"))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of("lf.span.name", "structural check")).parentObservation(parentObs).start();
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

            var chatResponse = structuralIssuesResponse.getResponse();

            if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                log.info("Hyperion structural check token usage: prompt={}, completion={}, total={}", usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
            }
            else {
                log.info("Hyperion structural check token usage not available for this provider/response");
            }

            // @formatter:on
            return toGenericConsistencyIssue(structuralIssuesResponse.entity());
        }
        catch (RuntimeException e) {
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
     * @param input prompt variables (rendered_context, programming_language)
     * @return semantic issues
     */
    private List<ConsistencyIssue> runSemanticCheck(Map<String, String> input, Observation parentObs) {
        var child = Observation.createNotStarted("hyperion.consistency.semantic", observationRegistry).contextualName("semantic check")
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of("ai.span", "true"))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of("lf.span.name", "semantic check")).parentObservation(parentObs).start();
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

            var chatResponse = semanticIssuesResponse.getResponse();

            if (chatResponse != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                log.info("Hyperion semantic check token usage: prompt={}, completion={}, total={}", usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
            }
            else {
                log.info("Hyperion semantic check token usage not available for this provider/response");
            }

            // @formatter:on
            return toGenericConsistencyIssue(semanticIssuesResponse.entity());
        }
        catch (RuntimeException e) {
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
