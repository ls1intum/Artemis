package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.BuildResultOutcome;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.PendingBuild;
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
 * The programming-exercise toolset for one agent round. One instance is created per round by
 * {@link ProgrammingVariantAdapters#createTools}; it is NOT a Spring bean — it carries per-round state
 * (checked-out repositories, last build outcomes, the touched-test-repo flag).
 *
 * All tools operate ONLY on the variant's repositories (never the source). Diff-style edits of existing files
 * ("transform, don't regenerate") are the main consistency lever. Validation errors (file not found, ambiguous
 * search text, unsafe path) are returned TO THE MODEL as the tool result.
 *
 * Cancellation: once the cancel flag is set, every tool short-circuits with an instruction to stop, so the
 * round converges quickly without doing further work; the pipeline performs the actual abort and cleanup at the
 * next round boundary (cancellation is cooperative and never interrupts a running LLM call).
 */
class ProgrammingVariantTools implements VariantToolset {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingVariantTools.class);

    /**
     * The only path segment the agent may never write to: git's own metadata directory. Everything else in the
     * repository — including build files (build.gradle, settings.gradle, pom.xml) and dotfiles — is editable,
     * because a domain re-theme legitimately has to rename the build's project/artifact name, and a generation
     * that cannot do so fails the build it is judged by.
     */
    private static final String GIT_METADATA_SEGMENT = ".git";

    private static final int MAX_FILE_CONTENT_LENGTH = 100_000;

    private static final int MAX_SEARCH_RESULTS = 50;

    private static final int MAX_SEARCH_LINE_LENGTH = 300;

    /** Per-round tool-call budget (see {@link #stopNotice()}); higher than the quiz budget — repo work needs more calls. */
    private static final int TOOL_CALL_BUDGET = 60;

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

    private int toolCallsUsed;

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

    @Override
    public List<ToolCallback> toolCallbacks() {
        return Arrays.asList(MethodToolCallbackProvider.builder().toolObjects(this).build().getToolCallbacks());
    }

    @Override
    public boolean touchedTestRepo() {
        return touchedTestRepo;
    }

    @Override
    public String finishSummary() {
        return finishSummary;
    }

    @Override
    public void flushPendingChanges() {
        for (Map.Entry<RepositoryType, Repository> entry : checkouts.entrySet()) {
            try {
                if (gitService.isWorkingCopyClean(entry.getValue())) {
                    continue;
                }
                log.info("Committing uncommitted {} repository changes left by the agent round for exercise {}", entry.getKey(), exercise.getId());
                repositoryService.commitChanges(entry.getValue(), user);
                markTouched(entry.getKey());
            }
            catch (GitAPIException e) {
                // Losing the commit means losing the round's work while verification would pass on the stale
                // pushed state — fail the round loudly instead.
                throw new IllegalStateException("Could not persist pending " + entry.getKey() + " repository changes after the agent round: " + e.getMessage(), e);
            }
        }
    }

    @Tool(description = "List all files in one of the variant exercise's repositories. Valid repositories: TEMPLATE, SOLUTION, TESTS.")
    public String listFiles(@ToolParam(description = "the repository to list: TEMPLATE, SOLUTION, or TESTS") String repository) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
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
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
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

    @Tool(description = "Search for a text snippet across all files of one of the variant exercise's repositories (TEMPLATE, SOLUTION, or TESTS). "
            + "Returns matching lines as 'path:lineNumber: line'. Use this to locate code before editing instead of reading files one by one.")
    public String searchFiles(@ToolParam(description = "the repository to search: TEMPLATE, SOLUTION, or TESTS") String repository,
            @ToolParam(description = "the exact text to search for (case-sensitive single-line substring, not a regex)") String searchText) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        if (searchText == null || searchText.isBlank()) {
            return "Error: the search text must not be empty.";
        }
        try {
            Repository checkout = checkout(repositoryType);
            List<String> paths = repositoryService.getFiles(checkout).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE).map(Map.Entry::getKey).sorted()
                    .toList();
            StringBuilder matches = new StringBuilder();
            int matchCount = 0;
            for (String path : paths) {
                String content;
                try {
                    content = new String(repositoryService.getFile(checkout, path), StandardCharsets.UTF_8);
                }
                catch (IOException e) {
                    continue;
                }
                String[] lines = content.split("\n", -1);
                for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                    if (!lines[lineIndex].contains(searchText)) {
                        continue;
                    }
                    if (matchCount == MAX_SEARCH_RESULTS) {
                        matches.append("[more matches omitted — make the search text more specific]");
                        return matches.toString();
                    }
                    matchCount++;
                    matches.append(path).append(':').append(lineIndex + 1).append(": ").append(truncateLine(lines[lineIndex].strip())).append('\n');
                }
            }
            return matchCount == 0 ? "No matches for the search text in the " + repositoryType + " repository." : matches.toString();
        }
        catch (Exception e) {
            return "Error: could not search the " + repositoryType + " repository: " + e.getMessage();
        }
    }

    @Tool(description = "Apply a search-and-replace edit to an EXISTING file in one of the variant's repositories. "
            + "The search text must occur exactly once in the file; otherwise the edit is rejected and you must make the search text more specific. "
            + "Prefer small, targeted edits over rewriting whole files — unchanged code must stay identical to the source. "
            + "Every file in the repository is editable, including build files (build.gradle, settings.gradle, pom.xml); only git's own .git directory is off limits.")
    public String applyEdit(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @ToolParam(description = "the file path relative to the repository root") String path,
            @ToolParam(description = "the exact text to search for; must match exactly one occurrence") String search,
            @ToolParam(description = "the replacement text") String replace) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        String normalizedPath = normalizeWritablePath(path);
        if (normalizedPath == null) {
            return unwritablePathMessage(path, repositoryType, "editable");
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
            EditOutcome outcome = applySearchReplace(content, normalizedPath, search, replace);
            if (outcome.failed()) {
                return "Error: " + outcome.error();
            }
            writeFileContent(checkout, normalizedPath, outcome.updatedContent());
            markTouched(repositoryType);
            return "Edit applied to '" + normalizedPath + "' in the " + repositoryType + " repository. Remember to run runBuild to verify your changes.";
        }
        catch (Exception e) {
            return "Error: could not edit '" + path + "' in the " + repositoryType + " repository: " + e.getMessage();
        }
    }

    /**
     * One search-and-replace edit for the batch {@link #applyEdits} tool. Same shape as a single
     * {@link #applyEdit} call: a repository-relative {@code path}, the unique {@code search} text, and its
     * {@code replace}ment.
     */
    public record BatchEdit(@JsonPropertyDescription("the file path relative to the repository root") String path,
            @JsonPropertyDescription("the exact text to search for; must match exactly one occurrence in the (current) file") String search,
            @JsonPropertyDescription("the replacement text") String replace) {
    }

    @Tool(description = "Apply MULTIPLE search-and-replace edits to files in ONE of the variant's repositories in a SINGLE call. "
            + "Strongly prefer this over many separate applyEdit round trips: gather every edit for a repository (for example all occurrences of a rename across several files) "
            + "and submit them as one batch. Edits are applied IN ORDER and each sees the effect of the previous ones. Each edit is reported independently as applied or with a "
            + "precise error, and a failed edit never blocks the others — only re-submit the failed ones in a follow-up call. Each edit's search text must occur exactly once in "
            + "its current file. Every file is editable, including build files (build.gradle, settings.gradle, pom.xml); only git's own .git directory is off limits.")
    public String applyEdits(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @ToolParam(description = "the edits to apply in order; each has path, search, and replace") List<BatchEdit> edits) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        if (edits == null || edits.isEmpty()) {
            return "Error: no edits were provided. Pass at least one { path, search, replace } edit.";
        }
        Repository checkout;
        try {
            checkout = checkout(repositoryType);
        }
        catch (Exception e) {
            return "Error: could not access the " + repositoryType + " repository: " + e.getMessage();
        }
        // In-memory content per touched file so later edits see earlier ones (sequential semantics) and each
        // file is read once and written once, instead of a read+delete+create per edit.
        Map<String, String> contents = new HashMap<>();
        Set<String> changedPaths = new LinkedHashSet<>();
        StringBuilder report = new StringBuilder();
        int appliedCount = 0;
        for (int index = 0; index < edits.size(); index++) {
            BatchEdit edit = edits.get(index);
            report.append("Edit ").append(index + 1).append(" (").append(edit == null ? "?" : edit.path()).append("): ");
            if (edit == null) {
                report.append("Error: the edit entry is missing.\n");
                continue;
            }
            String normalizedPath = normalizeWritablePath(edit.path());
            if (normalizedPath == null) {
                report.append("Error: '").append(edit.path()).append("' is not an editable path (must be relative to the repo root, no '..', not in .git).\n");
                continue;
            }
            String content = contents.get(normalizedPath);
            if (content == null) {
                try {
                    content = new String(repositoryService.getFile(checkout, normalizedPath), StandardCharsets.UTF_8);
                }
                catch (IOException e) {
                    report.append("Error: file '").append(edit.path()).append("' does not exist. Use listFiles to see the existing file paths.\n");
                    continue;
                }
                contents.put(normalizedPath, content);
            }
            EditOutcome outcome = applySearchReplace(content, normalizedPath, edit.search(), edit.replace());
            if (outcome.failed()) {
                report.append("Error: ").append(outcome.error()).append('\n');
                continue;
            }
            contents.put(normalizedPath, outcome.updatedContent());
            changedPaths.add(normalizedPath);
            appliedCount++;
            report.append("applied.\n");
        }
        for (String path : changedPaths) {
            try {
                writeFileContent(checkout, path, contents.get(path));
            }
            catch (IOException e) {
                report.append("Error: could not write '").append(path).append("': ").append(e.getMessage()).append('\n');
            }
        }
        if (appliedCount > 0) {
            markTouched(repositoryType);
        }
        report.append(appliedCount).append(" of ").append(edits.size()).append(" edit(s) applied to the ").append(repositoryType)
                .append(" repository. Remember to run runBuild to verify your changes.");
        return report.toString();
    }

    /**
     * Applies a single search-and-replace to {@code content} without touching the repository, so both the
     * single {@link #applyEdit} tool and the batch {@link #applyEdits} tool share one definition of a valid
     * edit (unique-match requirement, error wording). Returns the updated content or a precise error.
     */
    private static EditOutcome applySearchReplace(String content, String path, String search, String replace) {
        if (search == null || search.isEmpty()) {
            return EditOutcome.error("the search text must not be empty for '" + path + "'.");
        }
        int firstIndex = content.indexOf(search);
        if (firstIndex < 0) {
            return EditOutcome.error("the search text was not found in '" + path + "'. Read the file again and use the exact current text.");
        }
        if (content.indexOf(search, firstIndex + 1) >= 0) {
            return EditOutcome.error("the search text occurs more than once in '" + path + "'. Extend the search text so it matches exactly one occurrence.");
        }
        return EditOutcome.ok(content.substring(0, firstIndex) + replace + content.substring(firstIndex + search.length()));
    }

    /** Result of {@link #applySearchReplace}: either the updated content or a precise, model-facing error. */
    private record EditOutcome(String updatedContent, String error) {

        static EditOutcome ok(String updatedContent) {
            return new EditOutcome(updatedContent, null);
        }

        static EditOutcome error(String error) {
            return new EditOutcome(null, error);
        }

        boolean failed() {
            return error != null;
        }
    }

    @Tool(description = "Create a new file (or fully overwrite an existing one) in one of the variant's repositories. "
            + "Only use this for NEW files or complete rewrites; prefer applyEdit for changes to existing files. "
            + "Every path in the repository is writable except git's own .git directory.")
    public String writeFile(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @ToolParam(description = "the file path relative to the repository root") String path, @ToolParam(description = "the full new file content") String content) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        String normalizedPath = normalizeWritablePath(path);
        if (normalizedPath == null) {
            return unwritablePathMessage(path, repositoryType, "writable");
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

    @Tool(description = "Delete an existing file in one of the variant's repositories. Only use this for files the plan removes "
            + "(e.g. a test class dropped when making the exercise easier). Every path in the repository is deletable except git's own .git directory.")
    public String deleteFile(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @ToolParam(description = "the file path relative to the repository root") String path) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        String normalizedPath = normalizeWritablePath(path);
        if (normalizedPath == null) {
            return unwritablePathMessage(path, repositoryType, "deletable");
        }
        try {
            Repository checkout = checkout(repositoryType);
            if (gitService.getFileByName(checkout, normalizedPath).isEmpty()) {
                return "Error: file '" + path + "' does not exist in the " + repositoryType + " repository. Use listFiles to see the existing file paths.";
            }
            repositoryService.deleteFile(checkout, normalizedPath);
            markTouched(repositoryType);
            return "Deleted '" + normalizedPath + "' in the " + repositoryType + " repository. Remember to run runBuild to verify your changes.";
        }
        catch (Exception e) {
            return "Error: could not delete '" + normalizedPath + "' in the " + repositoryType + " repository: " + e.getMessage();
        }
    }

    @Tool(description = "Replace the variant exercise's problem statement (Markdown). Call this LAST, after the final test names are settled, "
            + "so every test referenced in the tasks actually exists in the test repository.")
    public String updateProblemStatement(@ToolParam(description = "the full new problem statement in Artemis Markdown") String problemStatement) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        if (problemStatement == null || problemStatement.isBlank()) {
            return "Error: the problem statement must not be empty.";
        }
        try {
            ProgrammingExercise persisted = programmingExerciseRepository.findByIdElseThrow(exercise.getId());
            persisted.setProblemStatement(ProgrammingVariantAdapters.stripPlantUmlCodeFences(problemStatement));
            programmingExerciseRepository.save(persisted);
            // Keep the task/test-case mapping in sync, exactly like the regular problem-statement update endpoint.
            programmingExerciseTaskService.updateTasksFromProblemStatement(persisted);
            exercise.setProblemStatement(persisted.getProblemStatement());
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
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
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
            // and mislead the agent (build-dependency constraint).
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

    @Tool(description = "Commit, push, and trigger the SOLUTION and TEMPLATE CI builds TOGETHER, then wait for BOTH results in one call. "
            + "Strongly prefer this over two separate runBuild calls whenever you need to check both (a green verify or a repair cycle): the two builds are independent and run "
            + "concurrently, so waiting jointly takes about as long as the slower single build instead of the sum of the two. "
            + "SOLUTION must pass 100% of tests; TEMPLATE must compile and execute at least one test but score 0%. "
            + "For the TESTS repository use runBuild — a tests change invalidates both other builds and must be rebuilt first on its own.")
    public String runBuilds() {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        Map<RepositoryType, PendingBuild> pending = new EnumMap<>(RepositoryType.class);
        StringBuilder report = new StringBuilder();
        for (RepositoryType repositoryType : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE)) {
            try {
                Repository checkout = checkout(repositoryType);
                repositoryService.commitChanges(checkout, user);
                LocalVCRepositoryUri repositoryUri = exercise.getRepositoryURI(repositoryType);
                String commitHash = gitService.getLastCommitHash(repositoryUri);
                ProgrammingExerciseParticipation participation = resolveParticipation(repositoryType);
                Instant triggeredAt = Instant.now();
                buildTrigger.triggerBuild(participation, commitHash, repositoryType);
                pending.put(repositoryType, new PendingBuild(commitHash, triggeredAt));
            }
            catch (ContinuousIntegrationException e) {
                report.append("Error: could not trigger the ").append(repositoryType).append(" build: ").append(e.getMessage()).append('\n');
            }
            catch (GitAPIException | RuntimeException e) {
                report.append("Error: could not prepare the ").append(repositoryType).append(" build: ").append(e.getMessage()).append('\n');
            }
        }
        if (pending.isEmpty()) {
            return report.append("No builds were triggered.").toString();
        }
        try {
            Map<RepositoryType, BuildResultOutcome> outcomes = buildVerificationService.waitForBuildResults(exercise, pending);
            for (RepositoryType repositoryType : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE)) {
                BuildResultOutcome outcome = outcomes.get(repositoryType);
                if (outcome == null) {
                    continue;
                }
                String description = describeOutcome(repositoryType, outcome);
                lastBuildResults.put(repositoryType, description);
                report.append("=== ").append(repositoryType).append(" build ===\n").append(description).append('\n');
            }
            return report.toString();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: the build wait was interrupted.";
        }
    }

    @Tool(description = "Get the detailed result of the most recent runBuild call for a repository (compiler output and failed test names/messages).")
    public String getBuildAndTestResults(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        return lastBuildResults.getOrDefault(repositoryType, "No build has been run for the " + repositoryType + " repository in this round yet. Use runBuild first.");
    }

    // returnDirect ends the internal tool loop immediately — no extra LLM round after the model finishes,
    // and the "budget exhausted, call finish" directive has a guaranteed exit.
    @Tool(returnDirect = true, description = "Signal that you are done with this round and provide a short summary of what you changed and verified.")
    public String finish(@ToolParam(description = "a short summary of the changes made in this round") String summary) {
        this.finishSummary = summary;
        return "Summary recorded. You are done with this round.";
    }

    /**
     * Combined stop check for cancellation and the per-round tool budget — every tool except finish
     * short-circuits with the returned directive.
     *
     * Short-circuit instead of throwing: Spring AI returns tool exceptions to the model as ordinary tool
     * results anyway, so an exception cannot abort the round — an explicit stop instruction converges the
     * round fastest. The pipeline performs the actual abort at the next round boundary.
     * The budget exists because Spring AI's internal tool loop has no iteration cap and a model that keeps
     * re-reading and re-reasoning would loop indefinitely.
     */
    private String stopNotice() {
        // Every tool call is a liveness signal for the long internal agent round (see the job's staleness handling).
        jobService.heartbeat(jobId);
        if (jobService.isCancelRequested(jobId)) {
            return "The variant generation job was CANCELLED. Do not call any more tools; the round is over and all further work will be discarded.";
        }
        toolCallsUsed++;
        if (toolCallsUsed > TOOL_CALL_BUDGET) {
            return "TOOL BUDGET EXHAUSTED for this round (" + TOOL_CALL_BUDGET + " calls). Do not call any other tool. Call finish NOW with a short summary of what you changed.";
        }
        return null;
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

    private static String truncateLine(String line) {
        return line.length() > MAX_SEARCH_LINE_LENGTH ? line.substring(0, MAX_SEARCH_LINE_LENGTH) + " [truncated]" : line;
    }

    private void writeFileContent(Repository repository, String path, String content) throws IOException {
        if (gitService.getFileByName(repository, path).isPresent()) {
            repositoryService.deleteFile(repository, path);
        }
        repositoryService.createFile(repository, path, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void markTouched(RepositoryType repositoryType) {
        if (repositoryType == RepositoryType.TESTS) {
            // A test-repo change invalidates every previously green build result (build-dependency constraint);
            // the verifier re-runs both builds with a freshness bound, so nothing stale is reused.
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

    private static String unwritablePathMessage(String path, RepositoryType repositoryType, String verb) {
        return "Error: '" + path + "' is not " + (verb.equals("editable") ? "an " : "a ") + verb + " path for the " + repositoryType
                + " repository. Paths must be relative to the repository root, must not contain '..', and must not point into the '.git' directory.";
    }

    /**
     * Normalizes a model-supplied path and rejects anything that would escape the repository working copy or
     * corrupt its git metadata. Every remaining path inside the repository is writable — see
     * {@link #GIT_METADATA_SEGMENT}.
     *
     * @return the normalized repository-relative path, or {@code null} when the path is not writable
     */
    private static String normalizeWritablePath(String rawPath) {
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
            if (normalized.isEmpty()) {
                return null;
            }
            for (String segment : normalized.split("/")) {
                if (GIT_METADATA_SEGMENT.equals(segment)) {
                    return null;
                }
            }
            return normalized;
        }
        catch (InvalidPathException e) {
            return null;
        }
    }
}
