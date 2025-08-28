package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.Severity;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionConsistencyCheckService {

    private static final Logger log = LoggerFactory.getLogger(HyperionConsistencyCheckService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    private final HyperionProgrammingExerciseContextRenderer exerciseContextRenderer;

    public HyperionConsistencyCheckService(ProgrammingExerciseRepository programmingExerciseRepository, @Autowired(required = false) ChatClient chatClient,
            HyperionPromptTemplateService templates, HyperionProgrammingExerciseContextRenderer exerciseContextRenderer) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient;
        this.templates = templates;
        this.exerciseContextRenderer = exerciseContextRenderer;
    }

    /**
     * Runs structural and semantic consistency checks on the given exercise using AI.
     *
     * @param user     the requesting user
     * @param exercise the exercise to check
     * @return aggregated issues
     * @throws NetworkingException when the AI service or data retrieval fails
     */
    public ConsistencyCheckResponseDTO checkConsistency(User user, ProgrammingExercise exercise) throws NetworkingException {
        log.info("Performing consistency check for exercise {} by user {}", exercise.getId(), user.getLogin());

        try {
            var exerciseWithParticipations = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
            if (chatClient == null) {
                throw new NetworkingException("Spring AI ChatClient is not configured");
            }

            String renderedContext = exerciseContextRenderer.renderContext(exerciseWithParticipations);

            String programmingLanguage = exerciseWithParticipations.getProgrammingLanguage() != null ? exerciseWithParticipations.getProgrammingLanguage().name() : "JAVA";
            var input = Map.<String, Object>of("rendered_context", renderedContext, "programming_language", programmingLanguage);

            Mono<List<ConsistencyIssue>> structuralMono = Mono.fromCallable(() -> toGeneric(runStructuralCheck("/prompts/hyperion/consistency_structural.st", input)))
                    .subscribeOn(Schedulers.boundedElastic()).onErrorResume(ex -> {
                        log.warn("Structural check failed, returning empty issues: {}", ex.getMessage());
                        return Mono.just(List.of());
                    });

            Mono<List<ConsistencyIssue>> semanticMono = Mono.fromCallable(() -> toGeneric(runSemanticCheck("/prompts/hyperion/consistency_semantic.st", input)))
                    .subscribeOn(Schedulers.boundedElastic()).onErrorResume(ex -> {
                        log.warn("Semantic check failed, returning empty issues: {}", ex.getMessage());
                        return Mono.just(List.of());
                    });

            List<ConsistencyIssue> combinedIssues;
            try {
                combinedIssues = Mono.zip(structuralMono, semanticMono, (a, b) -> {
                    List<ConsistencyIssue> combined = new ArrayList<>();
                    if (a != null) {
                        combined.addAll(a);
                    }
                    if (b != null) {
                        combined.addAll(b);
                    }
                    return combined;
                }).block();
                if (combinedIssues == null) {
                    combinedIssues = List.of();
                }
            }
            catch (RuntimeException e) {
                log.warn("Reactive parallel AI checks failed, falling back to sequential execution", e);
                StructuredOutputSchema.StructuralConsistencyIssues structuralEntity = runStructuralCheck("/prompts/hyperion/consistency_structural.st", input);
                StructuredOutputSchema.SemanticConsistencyIssues semanticEntity = runSemanticCheck("/prompts/hyperion/consistency_semantic.st", input);
                List<ConsistencyIssue> structural = toGeneric(structuralEntity);
                List<ConsistencyIssue> semantic = toGeneric(semanticEntity);
                List<ConsistencyIssue> merged = new ArrayList<>();
                merged.addAll(structural);
                merged.addAll(semantic);
                combinedIssues = merged;
            }

            List<ConsistencyIssueDTO> issueDTOs = combinedIssues.stream().map(this::mapConsistencyIssueToDto).collect(Collectors.toList());
            return new ConsistencyCheckResponseDTO(issueDTOs);
        }
        catch (TransientAiException e) {
            // Suggest retryable failure (e.g., 429/5xx). Let caller decide to retry at a higher level.
            log.warn("Transient AI error during consistency check: {}", e.getMessage());
            throw new NetworkingException("Temporary AI service issue. Please retry.", e);
        }
        catch (NonTransientAiException e) {
            // Non-retryable (e.g., invalid request, auth). Provide actionable message.
            log.error("Non-transient AI error during consistency check: {}", e.getMessage());
            throw new NetworkingException("AI request failed due to configuration or input. Check model and request.", e);
        }
        catch (Exception e) {
            throw new NetworkingException("An unexpected error occurred while performing consistency check", e);
        }
    }

    private StructuredOutputSchema.StructuralConsistencyIssues runStructuralCheck(String resourcePath, Map<String, Object> input) {
        String rendered = templates.render(resourcePath, Map.of("rendered_context", String.valueOf(input.getOrDefault("rendered_context", "")), "programming_language",
                String.valueOf(input.getOrDefault("programming_language", ""))));
        try {
            return chatClient.prompt().system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.").user(rendered).call()
                    .entity(StructuredOutputSchema.StructuralConsistencyIssues.class);
        }
        catch (TransientAiException e) {
            log.warn("Transient AI error in {}: {}", resourcePath, e.getMessage());
            return new StructuredOutputSchema.StructuralConsistencyIssues();
        }
        catch (NonTransientAiException e) {
            log.error("Non-transient AI error in {}: {}", resourcePath, e.getMessage());
            return new StructuredOutputSchema.StructuralConsistencyIssues();
        }
        catch (RuntimeException e) {
            // JSON mapping or unexpected client errors. Do not fail the whole request; return empty issues.
            log.error("Failed to obtain or parse AI response for {}", resourcePath, e);
            return new StructuredOutputSchema.StructuralConsistencyIssues();
        }
    }

    private StructuredOutputSchema.SemanticConsistencyIssues runSemanticCheck(String resourcePath, Map<String, Object> input) {
        String rendered = templates.render(resourcePath, Map.of("rendered_context", String.valueOf(input.getOrDefault("rendered_context", "")), "programming_language",
                String.valueOf(input.getOrDefault("programming_language", ""))));
        try {
            return chatClient.prompt().system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.").user(rendered).call()
                    .entity(StructuredOutputSchema.SemanticConsistencyIssues.class);
        }
        catch (TransientAiException e) {
            log.warn("Transient AI error in {}: {}", resourcePath, e.getMessage());
            return new StructuredOutputSchema.SemanticConsistencyIssues();
        }
        catch (NonTransientAiException e) {
            log.error("Non-transient AI error in {}: {}", resourcePath, e.getMessage());
            return new StructuredOutputSchema.SemanticConsistencyIssues();
        }
        catch (RuntimeException e) {
            // JSON mapping or unexpected client errors. Do not fail the whole request; return empty issues.
            log.error("Failed to obtain or parse AI response for {}", resourcePath, e);
            return new StructuredOutputSchema.SemanticConsistencyIssues();
        }
    }

    private ConsistencyIssueDTO mapConsistencyIssueToDto(ConsistencyIssue issue) {
        Severity severity = switch (issue.severity() == null ? "MEDIUM" : issue.severity().toUpperCase()) {
            case "LOW" -> Severity.LOW;
            case "HIGH" -> Severity.HIGH;
            default -> Severity.MEDIUM;
        };
        List<ArtifactLocationDTO> locations = issue.relatedLocations() == null ? List.of()
                : issue.relatedLocations().stream()
                        .map(loc -> new ArtifactLocationDTO(loc.type() == null ? ArtifactType.PROBLEM_STATEMENT : loc.type(), loc.filePath(), loc.startLine(), loc.endLine()))
                        .collect(Collectors.toList());
        ConsistencyIssueCategory category = issue.category() != null ? issue.category() : ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH;
        return new ConsistencyIssueDTO(severity, category, issue.description(), issue.suggestedFix(), locations);
    }

    // Grouped structured output schema for parsing AI responses
    private static class StructuredOutputSchema {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class StructuralConsistencyIssues {

            public List<StructuralConsistencyIssue> issues = List.of();
        }

        private enum StructuralConsistencyIssueCategory { // internal for parsing
            METHOD_RETURN_TYPE_MISMATCH, METHOD_PARAMETER_MISMATCH, CONSTRUCTOR_PARAMETER_MISMATCH, ATTRIBUTE_TYPE_MISMATCH, VISIBILITY_MISMATCH
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record StructuralConsistencyIssue(String severity, StructuralConsistencyIssueCategory category, String description, String suggestedFix,
                List<ArtifactLocation> relatedLocations) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class SemanticConsistencyIssues {

            public List<SemanticConsistencyIssue> issues = List.of();
        }

        private enum SemanticConsistencyIssueCategory { // internal for parsing
            IDENTIFIER_NAMING_INCONSISTENCY
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record SemanticConsistencyIssue(String severity, SemanticConsistencyIssueCategory category, String description, String suggestedFix,
                List<ArtifactLocation> relatedLocations) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ArtifactLocation(ArtifactType type, String filePath, Integer startLine, Integer endLine) {
        }
    }

    private List<ConsistencyIssue> toGeneric(StructuredOutputSchema.StructuralConsistencyIssues structural) {
        if (structural == null || structural.issues == null) {
            return List.of();
        }
        return structural.issues.stream().map(i -> new ConsistencyIssue(i.severity(), i.category() != null ? ConsistencyIssueCategory.valueOf(i.category().name()) : null,
                i.description(), i.suggestedFix(), i.relatedLocations())).toList();
    }

    private List<ConsistencyIssue> toGeneric(StructuredOutputSchema.SemanticConsistencyIssues semantic) {
        if (semantic == null || semantic.issues == null) {
            return List.of();
        }
        return semantic.issues.stream().map(i -> new ConsistencyIssue(i.severity(), i.category() != null ? ConsistencyIssueCategory.valueOf(i.category().name()) : null,
                i.description(), i.suggestedFix(), i.relatedLocations())).toList();
    }

    // Unified consistency issue used internally after parsing
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConsistencyIssue(String severity, ConsistencyIssueCategory category, String description, String suggestedFix,
            List<StructuredOutputSchema.ArtifactLocation> relatedLocations) {
    }
}
