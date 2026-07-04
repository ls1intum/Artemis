package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.BuildResultOutcome;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * The programming-exercise toolset for one agent round (plan Section 2.5 toolset table + Section 3
 * TRANSFORMING/VERIFYING rows). One instance is created per round by
 * {@link ProgrammingVariantAdapters#createTools}; it is NOT a Spring bean — it carries per-round state
 * (checked-out repositories, last build outcomes, the touched-test-repo flag).
 *
 * All tools operate ONLY on the variant's repositories (never the source). Diff-style edits of existing files
 * ("transform, don't regenerate") are the main consistency lever (plan Section 7). Validation errors (file not
 * found, ambiguous search text, unsafe path) are returned TO THE MODEL as the tool result (plan Section 6 row 2).
 * Every tool checks the job's cooperative cancel flag before running (plan Section 5.2).
 */
class ProgrammingVariantTools {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingVariantTools.class);

    /** Writes in template/solution repos are confined to this prefix (same whitelist as Hyperion codegen). */
    private static final String SOURCE_PATH_PREFIX = "src/";

    /** Writes in the test repo are confined to this prefix (same whitelist as Hyperion codegen). */
    private static final String TEST_PATH_PREFIX = "test/";

    private static final int MAX_FILE_CONTENT_LENGTH = 100_000;

    private final ProgrammingExercise exercise;

    private final User user;

    private final String jobId;

    private final ExerciseVariantJobService jobService;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final VariantBuildVerificationService buildVerificationService;

    private final VariantBuildTrigger buildTrigger;

    private final ProgrammingExerciseParticipationService participationService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final String defaultBranch;

    private final Map<RepositoryType, Repository> checkouts = new EnumMap<>(RepositoryType.class);

    private final Map<RepositoryType, String> lastBuildResults = new EnumMap<>(RepositoryType.class);

    private boolean touchedTestRepo;

    private String finishSummary;

    /** Small indirection so tests can stub CI triggering without a full CI setup. */
    @FunctionalInterface
    interface VariantBuildTrigger {

        void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType repositoryType) throws ContinuousIntegrationException;
    }

    ProgrammingVariantTools(ProgrammingExercise exercise, User user, String jobId, ExerciseVariantJobService jobService, GitService gitService, RepositoryService repositoryService,
            VariantBuildVerificationService buildVerificationService, VariantBuildTrigger buildTrigger, ProgrammingExerciseParticipationService participationService,
            ProgrammingSubmissionService programmingSubmissionService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService, String defaultBranch) {
        this.exercise = exercise;
        this.user = user;
        this.jobId = jobId;
        this.jobService = jobService;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.buildVerificationService = buildVerificationService;
        this.buildTrigger = buildTrigger;
        this.participationService = participationService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.defaultBranch = defaultBranch;
    }

    boolean hasTouchedTestRepo() {
        return touchedTestRepo;
    }

    String getFinishSummary() {
        return finishSummary;
    }

    @Tool(description = "List all files in one of the variant exercise's repositories. Valid repositories: TEMPLATE, SOLUTION, TESTS.")
    public String listFiles(@ToolParam(description = "the repository to list: TEMPLATE, SOLUTION, or TESTS") String repository) {
        checkCancelled();
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        try {
            Repository checkout = checkout(repositoryType);
            return repositoryService.getFiles(checkout).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE).map(Map.Entry::getKey).sorted()
                    .collect(Collectors.joining("\n"));
        }
        catch (Exception e) {
            return "Error: could not list files of the " + repositoryType + " repository: " + e.getMessage();
        }
    }

    @Tool(description = "Read the content of a file in one of the variant exercise's repositories (TEMPLATE, SOLUTION, or TESTS).")
    public String readFile(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @ToolParam(description = "the file path relative to the repository root, e.g. src/de/tum/Sorting.java") String path) {
        checkCancelled();
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        try {
            Repository checkout = checkout(repositoryType);
            String content = new String(repositoryService.getFile(checkout, path), StandardCharsets.UTF_8);
            if (content.length() > MAX_FILE_CONTENT_LENGTH) {
                return content.substring(0, MAX_FILE_CONTENT_LENGTH) + "\n[truncated]";
            }
            return content;
        }
        catch (IOException e) {
            return "Error: could not read file '" + path + "' in the " + repositoryType + " repository: " + e.getMessage() + ". Use listFiles to see the existing file paths.";
        }
        catch (Exception e) {
            return "Error: could not access the " + repositoryType + " repository: " + e.getMessage();
        }
    }

    @Tool(description = "Apply a search-and-replace edit to an EXISTING file in one of the variant's repositories. "
            + "The search text must occur exactly once in the file; otherwise the edit is rejected and you must make the search text more specific. "
            + "Prefer small, targeted edits over rewriting whole files — unchanged code must stay identical to the source. "
            + "Paths are restricted to src/ (TEMPLATE, SOLUTION) or test/ (TESTS).")
    public String applyEdit(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @ToolParam(description = "the file path relative to the repository root") String path,
            @ToolParam(description = "the exact text to search for; must match exactly one occurrence") String search,
            @ToolParam(description = "the replacement text") String replace) {
        checkCancelled();
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        // Same write whitelist as writeFile (and Hyperion codegen): edits outside the source root could corrupt
        // build/config files (pom.xml, build.gradle, CI config) the agent must not touch.
        String normalizedPath = normalizeWritablePath(path, repositoryType);
        if (normalizedPath == null) {
            String prefix = repositoryType == RepositoryType.TESTS ? TEST_PATH_PREFIX : SOURCE_PATH_PREFIX;
            return "Error: '" + path + "' is not an editable path for the " + repositoryType + " repository. Paths must be relative, must not contain '..' or hidden "
                    + "segments, and must start with '" + prefix + "'.";
        }
        try {
            Repository checkout = checkout(repositoryType);
            String content;
            try {
                content = new String(repositoryService.getFile(checkout, normalizedPath), StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                return "Error: file '" + path + "' does not exist in the " + repositoryType + " repository. Use listFiles to see the existing file paths.";
            }
            int firstIndex = content.indexOf(search);
            if (firstIndex < 0) {
                return "Error: the search text was not found in '" + path + "'. Read the file again and use the exact current text.";
            }
            if (content.indexOf(search, firstIndex + 1) >= 0) {
                return "Error: the search text occurs more than once in '" + path + "'. Extend the search text so it matches exactly one occurrence.";
            }
            String updated = content.substring(0, firstIndex) + replace + content.substring(firstIndex + search.length());
            writeFileContent(checkout, normalizedPath, updated);
            markTouched(repositoryType);
            return "Edit applied to '" + normalizedPath + "' in the " + repositoryType + " repository. Remember to run runBuild to verify your changes.";
        }
        catch (Exception e) {
            return "Error: could not edit '" + path + "' in the " + repositoryType + " repository: " + e.getMessage();
        }
    }

    @Tool(description = "Create a new file (or fully overwrite an existing one) in one of the variant's repositories. "
            + "Only use this for NEW files or complete rewrites; prefer applyEdit for changes to existing files. "
            + "Paths are restricted to src/ (TEMPLATE, SOLUTION) or test/ (TESTS).")
    public String writeFile(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @ToolParam(description = "the file path relative to the repository root; must start with src/ (TEMPLATE, SOLUTION) or test/ (TESTS)") String path,
            @ToolParam(description = "the full new file content") String content) {
        checkCancelled();
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        String normalizedPath = normalizeWritablePath(path, repositoryType);
        if (normalizedPath == null) {
            String prefix = repositoryType == RepositoryType.TESTS ? TEST_PATH_PREFIX : SOURCE_PATH_PREFIX;
            return "Error: '" + path + "' is not a writable path for the " + repositoryType + " repository. Paths must be relative, must not contain '..' or hidden "
                    + "segments, and must start with '" + prefix + "'.";
        }
        try {
            Repository checkout = checkout(repositoryType);
            writeFileContent(checkout, normalizedPath, content);
            markTouched(repositoryType);
            return "Wrote '" + normalizedPath + "' in the " + repositoryType + " repository. Remember to run runBuild to verify your changes.";
        }
        catch (Exception e) {
            return "Error: could not write '" + normalizedPath + "' in the " + repositoryType + " repository: " + e.getMessage();
        }
    }

    @Tool(description = "Replace the variant exercise's problem statement (Markdown). Call this LAST, after the final test names are settled, "
            + "so every test referenced in the tasks actually exists in the test repository.")
    public String updateProblemStatement(@ToolParam(description = "the full new problem statement in Artemis Markdown") String problemStatement) {
        checkCancelled();
        if (problemStatement == null || problemStatement.isBlank()) {
            return "Error: the problem statement must not be empty.";
        }
        try {
            ProgrammingExercise persisted = programmingExerciseRepository.findByIdElseThrow(exercise.getId());
            persisted.setProblemStatement(problemStatement);
            programmingExerciseRepository.save(persisted);
            // Keep the task/test-case mapping in sync, exactly like the regular problem-statement update endpoint.
            programmingExerciseTaskService.updateTasksFromProblemStatement(persisted);
            exercise.setProblemStatement(problemStatement);
            return "Problem statement updated.";
        }
        catch (Exception e) {
            return "Error: could not update the problem statement: " + e.getMessage();
        }
    }

    @Tool(description = "Commit and push all pending changes in the given repository, trigger a CI build, and wait for the result. "
            + "Build targets: SOLUTION must pass 100% of tests, TEMPLATE must compile but score 0% (tests must run and fail), TESTS must build successfully. "
            + "Changing the TESTS repository invalidates earlier SOLUTION/TEMPLATE results — re-run both afterwards.")
    public String runBuild(@ToolParam(description = "the repository to build: TEMPLATE, SOLUTION, or TESTS") String repository) {
        checkCancelled();
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        try {
            Repository checkout = checkout(repositoryType);
            repositoryService.commitChanges(checkout, user);
            LocalVCRepositoryUri repositoryUri = exercise.getRepositoryURI(repositoryType);
            String commitHash = gitService.getLastCommitHash(repositoryUri);
            ProgrammingExerciseParticipation participation = resolveParticipation(repositoryType);
            if (repositoryType == RepositoryType.TESTS) {
                programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), commitHash);
                markTouched(RepositoryType.TESTS);
            }
            Instant triggeredAt = Instant.now();
            try {
                buildTrigger.triggerBuild(participation, commitHash, repositoryType);
            }
            catch (ContinuousIntegrationException e) {
                return "Error: could not trigger the CI build for the " + repositoryType + " repository: " + e.getMessage();
            }
            // Freshness bound: only accept a result produced by THIS trigger. Without it, rebuilding an unchanged
            // solution/template commit after a test-repo edit would instantly return the stale pre-change result
            // and mislead the agent (plan Section 3, build-dependency constraint).
            BuildResultOutcome outcome = buildVerificationService.waitForBuildResult(exercise, commitHash, repositoryType, triggeredAt);
            String description = describeOutcome(repositoryType, outcome);
            lastBuildResults.put(repositoryType, description);
            return description;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: the build wait was interrupted.";
        }
        catch (GitAPIException | RuntimeException e) {
            return "Error: could not run the build for the " + repositoryType + " repository: " + e.getMessage();
        }
    }

    @Tool(description = "Get the detailed result of the most recent runBuild call for a repository (compiler output and failed test names/messages).")
    public String getBuildAndTestResults(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository) {
        checkCancelled();
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        return lastBuildResults.getOrDefault(repositoryType, "No build has been run for the " + repositoryType + " repository in this round yet. Use runBuild first.");
    }

    @Tool(description = "Signal that you are done with this round and provide a short summary of what you changed and verified.")
    public String finish(@ToolParam(description = "a short summary of the changes made in this round") String summary) {
        checkCancelled();
        this.finishSummary = summary;
        return "Summary recorded. You are done with this round.";
    }

    private void checkCancelled() {
        if (jobService.isCancelRequested(jobId)) {
            // Aborts the agent round between tool calls (plan Section 5.2: never mid-LLM-call or mid-build).
            throw new IllegalStateException("The variant generation job was cancelled");
        }
    }

    private Repository checkout(RepositoryType repositoryType) throws GitAPIException {
        Repository cached = checkouts.get(repositoryType);
        if (cached != null) {
            return cached;
        }
        LocalVCRepositoryUri repositoryUri = exercise.getRepositoryURI(repositoryType);
        if (repositoryUri == null) {
            throw new IllegalStateException("No " + repositoryType + " repository URI for exercise " + exercise.getId());
        }
        Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true, defaultBranch, false);
        if (repository == null) {
            throw new IllegalStateException("Could not check out the " + repositoryType + " repository");
        }
        checkouts.put(repositoryType, repository);
        return repository;
    }

    private void writeFileContent(Repository repository, String path, String content) throws IOException {
        if (gitService.getFileByName(repository, path).isPresent()) {
            repositoryService.deleteFile(repository, path);
        }
        repositoryService.createFile(repository, path, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void markTouched(RepositoryType repositoryType) {
        if (repositoryType == RepositoryType.TESTS) {
            // A test-repo change invalidates every previously green build result (plan Section 3, build-dependency
            // constraint); the verifier re-runs both builds with a freshness bound, so nothing stale is reused.
            touchedTestRepo = true;
        }
    }

    private ProgrammingExerciseParticipation resolveParticipation(RepositoryType repositoryType) {
        // TESTS reuses the solution participation: tests-repo builds run against the solution checkout.
        return switch (repositoryType) {
            case TEMPLATE -> participationService.findTemplateParticipationByProgrammingExerciseId(exercise.getId());
            case SOLUTION, TESTS -> participationService.retrieveSolutionParticipation(exercise);
            default -> throw new IllegalArgumentException("Unsupported repository type: " + repositoryType);
        };
    }

    private String describeOutcome(RepositoryType repositoryType, BuildResultOutcome outcome) {
        String target = switch (repositoryType) {
            case SOLUTION -> "the SOLUTION build must pass 100% of tests";
            case TEMPLATE -> "the TEMPLATE build must execute at least one test and score 0%";
            case TESTS -> "the TESTS build must succeed";
            default -> "";
        };
        return switch (outcome.state()) {
            case SUCCESS -> "Build target reached (" + target + ").\n" + buildVerificationService.describeBuildResult(outcome.result());
            case FAILED -> "Build target NOT reached (" + target + ").\n" + buildVerificationService.describeBuildResult(outcome.result());
            case TIMED_OUT -> "The build result did not arrive within the timeout (" + target + "). The build may still be running; you can retry runBuild.";
            case PARTICIPATION_NOT_FOUND -> "Internal error: no participation found for the " + repositoryType + " repository.";
            case CI_TRIGGER_FAILED -> "Internal error: the CI build could not be triggered.";
        };
    }

    private static RepositoryType parseRepositoryType(String repository) {
        if (repository == null) {
            return null;
        }
        return switch (repository.trim().toUpperCase()) {
            case "TEMPLATE" -> RepositoryType.TEMPLATE;
            case "SOLUTION" -> RepositoryType.SOLUTION;
            case "TESTS", "TEST" -> RepositoryType.TESTS;
            default -> null;
        };
    }

    private static String invalidRepositoryMessage(String repository) {
        return "Error: unknown repository '" + repository + "'. Valid values are TEMPLATE, SOLUTION, and TESTS.";
    }

    private static String normalizeWritablePath(String rawPath, RepositoryType repositoryType) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String sanitizedPath = rawPath.replace('\\', '/').trim();
        try {
            if (List.of(sanitizedPath.split("/")).contains("..")) {
                return null;
            }
            Path normalizedPath = Path.of(sanitizedPath).normalize();
            if (normalizedPath.isAbsolute()) {
                return null;
            }
            String normalized = normalizedPath.toString().replace('\\', '/');
            // Reject hidden segments (src/.env, test/.gitignore, ...) — same policy as Hyperion codegen.
            for (String segment : normalized.split("/")) {
                if (segment.startsWith(".")) {
                    return null;
                }
            }
            String prefix = repositoryType == RepositoryType.TESTS ? TEST_PATH_PREFIX : SOURCE_PATH_PREFIX;
            return normalized.startsWith(prefix) && normalized.length() > prefix.length() ? normalized : null;
        }
        catch (InvalidPathException e) {
            return null;
        }
    }
}
