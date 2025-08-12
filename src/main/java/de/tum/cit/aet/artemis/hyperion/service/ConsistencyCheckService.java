package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;
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
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.Severity;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Lazy
@Profile(PROFILE_HYPERION)
public class ConsistencyCheckService {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyCheckService.class);

    private final RepositoryService repositoryService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ChatClient chatClient;

    private final PromptTemplateService templates;

    public ConsistencyCheckService(RepositoryService repositoryService, ProgrammingExerciseRepository programmingExerciseRepository,
            @Autowired(required = false) ChatClient chatClient, PromptTemplateService templates) {
        this.repositoryService = repositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient;
        this.templates = templates;
    }

    public ConsistencyCheckResponseDTO checkConsistency(User user, ProgrammingExercise exercise) throws NetworkingException {
        log.info("Performing consistency check for exercise {} by user {}", exercise.getId(), user.getLogin());

        try {
            var exerciseWithParticipations = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
            if (chatClient == null) {
                throw new NetworkingException("Spring AI ChatClient is not configured");
            }

            var templateRepo = buildRepository(exerciseWithParticipations, RepositoryType.TEMPLATE);
            var solutionRepo = buildRepository(exerciseWithParticipations, RepositoryType.SOLUTION);
            var testRepo = buildRepository(exerciseWithParticipations, RepositoryType.TESTS);

            var lang = exerciseWithParticipations.getProgrammingLanguage();
            List<ContextRenderer.RepoFile> templateFiles = ContextRenderer
                    .filterFilesByLanguage(templateRepo.files().stream().map(f -> new ContextRenderer.RepoFile(f.path(), f.content())).toList(), lang);
            List<ContextRenderer.RepoFile> solutionFiles = ContextRenderer
                    .filterFilesByLanguage(solutionRepo.files().stream().map(f -> new ContextRenderer.RepoFile(f.path(), f.content())).toList(), lang);
            List<ContextRenderer.RepoFile> testFiles = ContextRenderer
                    .filterFilesByLanguage(testRepo.files().stream().map(f -> new ContextRenderer.RepoFile(f.path(), f.content())).toList(), lang);

            String renderedContext = String
                    .join("\n\n", List.of(
                            ContextRenderer.renderRepository(
                                    List.of(new ContextRenderer.RepoFile("problem_statement.md",
                                            exerciseWithParticipations.getProblemStatement() != null ? exerciseWithParticipations.getProblemStatement() : "")),
                                    "Problem Statement"),
                            ContextRenderer.renderRepository(templateFiles, "Template Repository"), ContextRenderer.renderRepository(solutionFiles, "Solution Repository"),
                            ContextRenderer.renderRepository(testFiles, "Test Repository")));

            String programmingLanguage = exerciseWithParticipations.getProgrammingLanguage() != null ? exerciseWithParticipations.getProgrammingLanguage().name() : "JAVA";
            var input = Map.<String, Object>of("rendered_context", renderedContext, "programming_language", programmingLanguage);

            Mono<List<ConsistencyIssue>> structuralMono = Mono.fromCallable(() -> runConsistencyCheck("/prompts/hyperion/structural.st", input).issues())
                    .subscribeOn(Schedulers.boundedElastic()).onErrorResume(ex -> {
                        log.warn("Structural check failed, returning empty issues: {}", ex.getMessage());
                        return Mono.just(List.of());
                    });

            Mono<List<ConsistencyIssue>> semanticMono = Mono.fromCallable(() -> runConsistencyCheck("/prompts/hyperion/semantic.st", input).issues())
                    .subscribeOn(Schedulers.boundedElastic()).onErrorResume(ex -> {
                        log.warn("Semantic check failed, returning empty issues: {}", ex.getMessage());
                        return Mono.just(List.of());
                    });

            List<ConsistencyIssue> combinedIssues;
            try {
                combinedIssues = Mono.zip(structuralMono, semanticMono, (a, b) -> {
                    List<ConsistencyIssue> combined = new ArrayList<>();
                    if (a != null)
                        combined.addAll(a);
                    if (b != null)
                        combined.addAll(b);
                    return combined;
                }).block();
                if (combinedIssues == null) {
                    combinedIssues = List.of();
                }
            }
            catch (RuntimeException e) {
                log.warn("Reactive parallel AI checks failed, falling back to sequential execution", e);
                ConsistencyIssues structuralEntity = runConsistencyCheck("/prompts/hyperion/structural.st", input);
                ConsistencyIssues semanticEntity = runConsistencyCheck("/prompts/hyperion/semantic.st", input);
                List<ConsistencyIssue> structural = structuralEntity != null ? structuralEntity.issues() : List.of();
                List<ConsistencyIssue> semantic = semanticEntity != null ? semanticEntity.issues() : List.of();
                List<ConsistencyIssue> merged = new ArrayList<>();
                merged.addAll(structural);
                merged.addAll(semantic);
                combinedIssues = merged;
            }

            List<ConsistencyIssueDTO> issueDTOs = combinedIssues.stream().map(this::mapConsistencyIssueToDto).collect(Collectors.toList());
            boolean hasIssues = !issueDTOs.isEmpty();
            String summary = hasIssues ? String.format("Found %d consistency issue(s)", issueDTOs.size()) : "No issues found";
            return new ConsistencyCheckResponseDTO(issueDTOs, hasIssues, summary);
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

    private ConsistencyIssues runConsistencyCheck(String resourcePath, Map<String, Object> input) {
        String rendered = templates.render(resourcePath, Map.of("rendered_context", String.valueOf(input.getOrDefault("rendered_context", "")), "programming_language",
                String.valueOf(input.getOrDefault("programming_language", ""))));
        try {
            return chatClient.prompt().system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.").user(rendered).call()
                    .entity(ConsistencyIssues.class);
        }
        catch (TransientAiException e) {
            log.warn("Transient AI error in {}: {}", resourcePath, e.getMessage());
            return new ConsistencyIssues();
        }
        catch (NonTransientAiException e) {
            log.error("Non-transient AI error in {}: {}", resourcePath, e.getMessage());
            return new ConsistencyIssues();
        }
        catch (RuntimeException e) {
            // JSON mapping or unexpected client errors. Do not fail the whole request; return empty issues.
            log.error("Failed to obtain or parse AI response for {}", resourcePath, e);
            return new ConsistencyIssues();
        }
    }

    private Repo buildRepository(ProgrammingExercise exercise, RepositoryType repositoryType) throws IOException {
        List<RepoFile> repositoryFiles;
        try {
            switch (repositoryType) {
                case TEMPLATE -> {
                    var templateParticipation = exercise.getTemplateParticipation();
                    repositoryFiles = getFilteredRepositoryFiles(templateParticipation);
                }
                case SOLUTION -> {
                    var solutionParticipation = exercise.getSolutionParticipation();
                    repositoryFiles = getFilteredRepositoryFiles(solutionParticipation);
                }
                case TESTS -> {
                    repositoryFiles = getRepositoryFiles(exercise.getVcsTestRepositoryUri());
                }
                default -> {
                    log.warn("Unknown repository type: {}", repositoryType);
                    repositoryFiles = List.of();
                }
            }
        }
        catch (Exception e) {
            log.error("Failed to fetch repository contents for {} repository of exercise {}", repositoryType, exercise.getId(), e);
            repositoryFiles = List.of();
        }
        return new Repo(repositoryFiles);
    }

    private List<RepoFile> getFilteredRepositoryFiles(ProgrammingExerciseParticipation participation) {
        var language = participation.getProgrammingExercise().getProgrammingLanguage();
        var repositoryContents = getRepositoryContents(participation.getVcsRepositoryUri());
        return repositoryContents.entrySet().stream().filter(entry -> language == null || language.matchesFileExtension(entry.getKey()))
                .map(entry -> new RepoFile(entry.getKey(), entry.getValue())).collect(Collectors.toList());
    }

    private List<RepoFile> getRepositoryFiles(VcsRepositoryUri repositoryUri) {
        var repositoryContents = getRepositoryContents(repositoryUri);
        return repositoryContents.entrySet().stream().map(entry -> new RepoFile(entry.getKey(), entry.getValue())).collect(Collectors.toList());
    }

    private java.util.Map<String, String> getRepositoryContents(VcsRepositoryUri repositoryUri) {
        try {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repositoryUri);
        }
        catch (IOException e) {
            log.error("Could not get repository content from {}", repositoryUri, e);
            return java.util.Map.of();
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
        return new ConsistencyIssueDTO(severity, issue.category(), issue.description(), issue.suggestedFix(), locations);
    }

    private record Repo(List<RepoFile> files) {
    }

    private record RepoFile(String path, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ConsistencyIssues {

        public List<ConsistencyIssue> issues = List.of();

        public ConsistencyIssues() {
        }

        public List<ConsistencyIssue> issues() {
            return issues == null ? List.of() : issues;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConsistencyIssue(String severity, String category, String description, String suggestedFix, List<ArtifactLocation> relatedLocations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ArtifactLocation(ArtifactType type, String filePath, Integer startLine, Integer endLine) {
    }
}
