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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.Severity;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Service for reviewing and refining programming exercises using the Hyperion service.
 */
@Service
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionReviewAndRefineService {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewAndRefineService.class);

    private final RepositoryService repositoryService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ChatClient chatClient;

    private final ObjectMapper objectMapper;

    public HyperionReviewAndRefineService(RepositoryService repositoryService, ProgrammingExerciseRepository programmingExerciseRepository,
            @Autowired(required = false) ChatClient chatClient) {
        this.repositoryService = repositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.chatClient = chatClient; // may be null if Spring AI isn't configured
        this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Performs a consistency check on a programming exercise using the Hyperion service.
     * Analyzes the relationship between problem statement, template code, solution code, and test cases.
     *
     * @param user     the user requesting the consistency check
     * @param exercise the programming exercise to analyze
     * @return DTO containing identified inconsistencies and their descriptions
     * @throws NetworkingException if communication with Hyperion service fails
     */
    public ConsistencyCheckResponseDTO checkConsistency(User user, ProgrammingExercise exercise) throws NetworkingException {
        log.info("Performing consistency check for exercise {} by user {}", exercise.getId(), user.getLogin());

        try {
            // Load the exercise with participations to avoid lazy loading issues
            var exerciseWithParticipations = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

            if (chatClient == null) {
                throw new NetworkingException("Spring AI ChatClient is not configured");
            }

            log.info("Using local Spring AI for consistency check");

            // Build input context using exact renderer parity with Python
            var templateRepo = buildRepository(exerciseWithParticipations, RepositoryType.TEMPLATE);
            var solutionRepo = buildRepository(exerciseWithParticipations, RepositoryType.SOLUTION);
            var testRepo = buildRepository(exerciseWithParticipations, RepositoryType.TESTS);

            String programmingLanguage = exerciseWithParticipations.getProgrammingLanguage() != null ? exerciseWithParticipations.getProgrammingLanguage().name() : "JAVA";

            // Filter files by language for each repo
            var lang = exerciseWithParticipations.getProgrammingLanguage();
            List<ContextRenderer.RepoFile> templateFiles = templateRepo.files().stream().map(f -> new ContextRenderer.RepoFile(f.path(), f.content())).toList();
            List<ContextRenderer.RepoFile> solutionFiles = solutionRepo.files().stream().map(f -> new ContextRenderer.RepoFile(f.path(), f.content())).toList();
            List<ContextRenderer.RepoFile> testFiles = testRepo.files().stream().map(f -> new ContextRenderer.RepoFile(f.path(), f.content())).toList();
            templateFiles = ContextRenderer.filterFilesByLanguage(templateFiles, lang);
            solutionFiles = ContextRenderer.filterFilesByLanguage(solutionFiles, lang);
            testFiles = ContextRenderer.filterFilesByLanguage(testFiles, lang);

            String renderedContext = String
                    .join("\n\n", List.of(
                            ContextRenderer.renderRepository(
                                    List.of(new ContextRenderer.RepoFile("problem_statement.md",
                                            exerciseWithParticipations.getProblemStatement() != null ? exerciseWithParticipations.getProblemStatement() : "")),
                                    "Problem Statement"),
                            ContextRenderer.renderRepository(templateFiles, "Template Repository"), ContextRenderer.renderRepository(solutionFiles, "Solution Repository"),
                            ContextRenderer.renderRepository(testFiles, "Test Repository")));

            var input = Map.<String, Object>of("rendered_context", renderedContext, "programming_language", programmingLanguage);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            CompletableFuture<List<AiIssue>> structuralFuture = CompletableFuture
                    .supplyAsync(() -> runAiCheckEntity("structural", buildStructuralPrompt(), input, IssuesEntity.class), executor).thenApply(IssuesEntity::issues);
            CompletableFuture<List<AiIssue>> semanticFuture = CompletableFuture
                    .supplyAsync(() -> runAiCheckEntity("semantic", buildSemanticPrompt(), input, IssuesEntity.class), executor).thenApply(IssuesEntity::issues);

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
                IssuesEntity structuralEntity = runAiCheckEntity("structural", buildStructuralPrompt(), input, IssuesEntity.class);
                IssuesEntity semanticEntity = runAiCheckEntity("semantic", buildSemanticPrompt(), input, IssuesEntity.class);
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
            log.info("Consistency check completed for exercise {} via Spring AI", exercise.getId());
            return new ConsistencyCheckResponseDTO(issueDTOs, hasIssues, summary);

        }
        catch (Exception e) {
            log.error("Failed to perform consistency check for exercise {} by user {}", exercise.getId(), user.getLogin(), e);
            throw new NetworkingException("An error occurred while performing consistency check", e);
        }
    }

    /**
     * Rewrites and improves a problem statement using the Hyperion service.
     *
     * @param user                 the user requesting the problem statement rewrite
     * @param course               the course context (used for logging and tracking)
     * @param problemStatementText the original problem statement text to improve
     * @return DTO containing the improved problem statement text
     * @throws NetworkingException      if communication with the Hyperion service fails
     * @throws IllegalArgumentException if any parameter is null or problemStatementText is empty
     */
    public ProblemStatementRewriteResponseDTO rewriteProblemStatement(User user, Course course, String problemStatementText) throws NetworkingException {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        if (course == null) {
            throw new IllegalArgumentException("Course must not be null");
        }
        if (problemStatementText == null || problemStatementText.trim().isEmpty()) {
            throw new IllegalArgumentException("Problem statement text must not be null or empty");
        }

        log.info("Rewriting problem statement for course {} by user {}", course.getId(), user.getLogin());

        try {
            if (chatClient == null) {
                throw new NetworkingException("Spring AI ChatClient is not configured");
            }

            // Local Spring AI rewrite
            String prompt = buildRewritePrompt().replace("{text}", problemStatementText.trim());
            String content = chatClient.prompt()
                    .system("You are an expert technical writing assistant for programming exercise problem statements. Return only the rewritten statement, no explanations.")
                    .user(prompt).call().content();

            if (content == null || content.trim().isEmpty()) {
                log.warn("Spring AI returned empty rewritten text for course {}", course.getId());
                throw new NetworkingException("AI returned empty response");
            }

            String result = content.trim();
            boolean improved = !result.equals(problemStatementText.trim());
            log.info("Problem statement rewrite completed for course {} via Spring AI", course.getId());
            return new ProblemStatementRewriteResponseDTO(result, improved);
        }
        catch (Exception e) {
            log.error("Failed to rewrite problem statement for course {} by user {}", course.getId(), user.getLogin(), e);
            throw new NetworkingException("An error occurred while rewriting problem statement", e);
        }
    }

    /**
     * Extracts repository contents and builds an internal repository object for AI prompts.
     *
     * @param exercise       the programming exercise containing repository information
     * @param repositoryType the type of repository to extract (TEMPLATE, SOLUTION, or TESTS)
     * @return a Repo object containing filtered file contents, or empty Repo if access fails
     * @throws IOException if repository operations encounter unrecoverable errors
     */
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

    private <T> T runAiCheckEntity(String checkName, String promptTemplate, Map<String, Object> input, Class<T> entityClass) {
        String formatted = promptTemplate.replace("{rendered_context}", String.valueOf(input.getOrDefault("rendered_context", ""))).replace("{programming_language}",
                String.valueOf(input.getOrDefault("programming_language", "")));
        return chatClient.prompt().system("You are a senior code review assistant for programming exercises. Return only JSON matching the schema.").user(formatted).call()
                .entity(entityClass);
    }

    private String buildStructuralPrompt() {
        return """
                Analyze the programming exercise for structural consistency issues between the problem statement, template code, solution code, and tests.
                Programming language: {programming_language}

                Context:
                {rendered_context}

                        Focus on the following categories only:
                        - METHOD_RETURN_TYPE_MISMATCH
                        - METHOD_PARAMETER_MISMATCH
                        - CONSTRUCTOR_PARAMETER_MISMATCH
                        - ATTRIBUTE_TYPE_MISMATCH
                        - VISIBILITY_MISMATCH

                        Return a JSON object with the following schema strictly:
                        {
                            "issues": [
                                {
                                    "severity": "LOW" | "MEDIUM" | "HIGH",
                                    "category": "METHOD_RETURN_TYPE_MISMATCH" | "METHOD_PARAMETER_MISMATCH" | "CONSTRUCTOR_PARAMETER_MISMATCH" | "ATTRIBUTE_TYPE_MISMATCH" | "VISIBILITY_MISMATCH",
                                    "description": string,
                                    "suggestedFix": string,
                                    "relatedLocations": [
                                        {
                                            "type": "PROBLEM_STATEMENT" | "TEMPLATE_REPOSITORY" | "SOLUTION_REPOSITORY",
                                            "filePath": string,
                                            "startLine": number | null,
                                            "endLine": number | null
                                        }
                                    ]
                                }
                            ]
                        }
                        """;
    }

    private String buildSemanticPrompt() {
        return """
                        Analyze the programming exercise for semantic consistency issues focused on identifier naming consistency across problem statement, template, solution, and tests.
                        Programming language: {programming_language}

                Context:
                {rendered_context}

                        Focus on the following category only:
                        - IDENTIFIER_NAMING_INCONSISTENCY

                        Return a JSON object with the following schema strictly:
                        {
                            "issues": [
                                {
                                    "severity": "LOW" | "MEDIUM" | "HIGH",
                                    "category": "IDENTIFIER_NAMING_INCONSISTENCY",
                                    "description": string,
                                    "suggestedFix": string,
                                    "relatedLocations": [
                                        {
                                            "type": "PROBLEM_STATEMENT" | "TEMPLATE_REPOSITORY" | "SOLUTION_REPOSITORY",
                                            "filePath": string,
                                            "startLine": number | null,
                                            "endLine": number | null
                                        }
                                    ]
                                }
                            ]
                        }
                        """;
    }

    private String buildRewritePrompt() {
        return """
                You are tasked with rewriting a programming exercise problem statement.
                Goals:
                - Improve clarity, conciseness, and coherence while preserving the original intent.
                - Maintain technical correctness and avoid changing the required functionality.
                - Remove ambiguity; ensure inputs, outputs, constraints, and acceptance criteria are explicit when present.
                - Keep markdown structure (headings, lists, code blocks) and formatting.
                - Avoid adding extraneous explanations; do not include solution hints.

                Original problem statement:
                {text}

                Output ONLY the rewritten problem statement as plain text (no JSON, no commentary).
                """;
    }

    /**
     * Retrieves and filters repository contents for a programming exercise participation.
     * Filters by programming language to include only relevant source files.
     *
     * @param participation the programming exercise participation with VCS repository access
     * @return list of RepositoryFile objects containing filtered source files
     */
    private List<RepoFile> getFilteredRepositoryFiles(ProgrammingExerciseParticipation participation) {
        var language = participation.getProgrammingExercise().getProgrammingLanguage();
        var repositoryContents = getRepositoryContents(participation.getVcsRepositoryUri());

        return repositoryContents.entrySet().stream().filter(entry -> language == null || language.matchesFileExtension(entry.getKey()))
                .map(entry -> new RepoFile(entry.getKey(), entry.getValue())).collect(Collectors.toList());
    }

    /**
     * Retrieves all files from a VCS repository and converts them to RepositoryFile objects.
     * This method is used primarily for test repositories where no programming language
     * filtering is applied, as test files may include various formats and configurations.
     *
     * @param repositoryUri the VCS repository URI to access
     * @return list of RepositoryFile objects containing all repository files
     */
    private List<RepoFile> getRepositoryFiles(VcsRepositoryUri repositoryUri) {
        var repositoryContents = getRepositoryContents(repositoryUri);

        return repositoryContents.entrySet().stream().map(entry -> new RepoFile(entry.getKey(), entry.getValue())).collect(Collectors.toList());
    }

    /**
     * Safely retrieves repository contents from a VCS repository with error handling.
     * Returns an empty map if the repository cannot be accessed rather than propagating exceptions.
     * This allows consistency checks to proceed with partial data when some repositories are unavailable.
     *
     * @param repositoryUri the VCS repository URI to access
     * @return map of file paths to file contents, or empty map if repository access fails
     */
    private java.util.Map<String, String> getRepositoryContents(VcsRepositoryUri repositoryUri) {
        try {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repositoryUri);
        }
        catch (IOException e) {
            log.error("Could not get repository content from {}", repositoryUri, e);
            return java.util.Map.of();
        }
    }

    // Lightweight internal repo models
    private record Repo(List<RepoFile> files) {
    }

    private record RepoFile(String path, String content) {
    }

    private String renderRepo(Repo repo) {
        // Legacy method not used anymore; kept for backward compatibility in case of future use
        return "(unused)";
    }

    // ----- Internal AI Response Models -----

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
