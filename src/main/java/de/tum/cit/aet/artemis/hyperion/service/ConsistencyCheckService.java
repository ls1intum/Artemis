package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CompletableFuture<List<AiIssue>> structuralFuture = CompletableFuture
                    .supplyAsync(() -> runAiCheckEntity("/prompts/hyperion/structural.st", input, IssuesEntity.class), executor).thenApply(IssuesEntity::issues);
            CompletableFuture<List<AiIssue>> semanticFuture = CompletableFuture
                    .supplyAsync(() -> runAiCheckEntity("/prompts/hyperion/semantic.st", input, IssuesEntity.class), executor).thenApply(IssuesEntity::issues);

            List<AiIssue> combinedIssues;
            try {
                combinedIssues = structuralFuture.thenCombine(semanticFuture, (a, b) -> {
                    List<AiIssue> combined = new java.util.ArrayList<>();
                    if (a != null)
                        combined.addAll(a);
                    if (b != null)
                        combined.addAll(b);
                    return combined;
                }).get();
            }
            catch (InterruptedException | ExecutionException e) {
                log.warn("Parallel AI checks failed, falling back to sequential execution", e);
                IssuesEntity structuralEntity = runAiCheckEntity("/prompts/hyperion/structural.st", input, IssuesEntity.class);
                IssuesEntity semanticEntity = runAiCheckEntity("/prompts/hyperion/semantic.st", input, IssuesEntity.class);
                List<AiIssue> structural = structuralEntity != null ? structuralEntity.issues() : List.of();
                List<AiIssue> semantic = semanticEntity != null ? semanticEntity.issues() : List.of();
                List<AiIssue> merged = new java.util.ArrayList<>();
                merged.addAll(structural);
                merged.addAll(semantic);
                combinedIssues = merged;
            }
            finally {
                executor.shutdown();
            }

            List<ConsistencyIssueDTO> issueDTOs = combinedIssues.stream().map(this::mapAiIssueToDto).collect(Collectors.toList());
            boolean hasIssues = !issueDTOs.isEmpty();
            String summary = hasIssues ? String.format("Found %d consistency issue(s)", issueDTOs.size()) : "No issues found";
            return new ConsistencyCheckResponseDTO(issueDTOs, hasIssues, summary);
        }
        catch (Exception e) {
            throw new NetworkingException("An error occurred while performing consistency check", e);
        }
    }

    private <T> T runAiCheckEntity(String resourcePath, Map<String, Object> input, Class<T> entityClass) {
        String rendered = templates.render(resourcePath, Map.of("rendered_context", String.valueOf(input.getOrDefault("rendered_context", "")), "programming_language",
                String.valueOf(input.getOrDefault("programming_language", ""))));
        return chatClient.prompt().system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.").user(rendered).call()
                .entity(entityClass);
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

    private ConsistencyIssueDTO mapAiIssueToDto(AiIssue issue) {
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
    private static class IssuesEntity {

        public List<AiIssue> issues = List.of();

        public IssuesEntity() {
        }

        public List<AiIssue> issues() {
            return issues == null ? List.of() : issues;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiIssue(String severity, String category, String description, String suggestedFix, List<AiArtifactLocation> relatedLocations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiArtifactLocation(ArtifactType type, String filePath, Integer startLine, Integer endLine) {
    }
}
