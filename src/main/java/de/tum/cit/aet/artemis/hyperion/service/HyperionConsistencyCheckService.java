package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
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
     * @return aggregated consistency issues
     */
    public ConsistencyCheckResponseDTO checkConsistency(User user, ProgrammingExercise exercise) {
        log.info("Performing consistency check for exercise {} by user {}", exercise.getId(), user.getLogin());
        var exerciseWithParticipations = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        String renderedRepositoryContext = exerciseContextRenderer.renderContext(exerciseWithParticipations);
        String programmingLanguage = exerciseWithParticipations.getProgrammingLanguage() != null ? exerciseWithParticipations.getProgrammingLanguage().name() : "JAVA";
        var input = Map.of("rendered_context", renderedRepositoryContext, "programming_language", programmingLanguage);

        Mono<List<ConsistencyIssue>> structuralMono = Mono.fromCallable(() -> runStructuralCheck(input)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());

        Mono<List<ConsistencyIssue>> semanticMono = Mono.fromCallable(() -> runSemanticCheck(input)).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(List.of());

        List<ConsistencyIssue> combinedIssues = new ArrayList<>();
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

        List<ConsistencyIssueDTO> issueDTOs = combinedIssues.stream().map(this::mapConsistencyIssueToDto).collect(Collectors.toList());
        return new ConsistencyCheckResponseDTO(issueDTOs);
    }

    private List<ConsistencyIssue> runStructuralCheck(Map<String, String> input) {
        var resourcePath = "/prompts/hyperion/consistency_structural.st";
        String renderedPrompt = templates.render(resourcePath, input);
        try {
            var structuralIssues = chatClient.prompt().system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.")
                    .user(renderedPrompt).call().entity(StructuredOutputSchema.StructuralConsistencyIssues.class);
            return toGenericConsistencyIssue(structuralIssues);
        }
        catch (RuntimeException e) {
            // JSON mapping or unexpected client errors. Do not fail the whole request; return empty issues.
            log.error("Failed to obtain or parse AI response for {}", resourcePath, e);
            return new ArrayList<>();
        }
    }

    private List<ConsistencyIssue> runSemanticCheck(Map<String, String> input) {
        var resourcePath = "/prompts/hyperion/consistency_semantic.st";
        String renderedPrompt = templates.render(resourcePath, input);
        try {
            var semanticIssues = chatClient.prompt().system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.")
                    .user(renderedPrompt).call().entity(StructuredOutputSchema.SemanticConsistencyIssues.class);
            return toGenericConsistencyIssue(semanticIssues);
        }
        catch (RuntimeException e) {
            // JSON mapping or unexpected client errors. Do not fail the whole request; return empty issues.
            log.error("Failed to obtain or parse AI response for {}", resourcePath, e);
            return new ArrayList<>();
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

    private List<ConsistencyIssue> toGenericConsistencyIssue(StructuredOutputSchema.StructuralConsistencyIssues structuralIssues) {
        if (structuralIssues == null || structuralIssues.issues == null) {
            return List.of();
        }
        return structuralIssues.issues.stream().map(i -> new ConsistencyIssue(i.severity(), i.category() != null ? ConsistencyIssueCategory.valueOf(i.category().name()) : null,
                i.description(), i.suggestedFix(), i.relatedLocations())).toList();
    }

    private List<ConsistencyIssue> toGenericConsistencyIssue(StructuredOutputSchema.SemanticConsistencyIssues semanticIssues) {
        if (semanticIssues == null || semanticIssues.issues == null) {
            return List.of();
        }
        return semanticIssues.issues.stream().map(i -> new ConsistencyIssue(i.severity(), i.category() != null ? ConsistencyIssueCategory.valueOf(i.category().name()) : null,
                i.description(), i.suggestedFix(), i.relatedLocations())).toList();
    }

    // Unified consistency issue used internally after parsing
    private record ConsistencyIssue(String severity, ConsistencyIssueCategory category, String description, String suggestedFix,
            List<StructuredOutputSchema.ArtifactLocation> relatedLocations) {
    }

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
