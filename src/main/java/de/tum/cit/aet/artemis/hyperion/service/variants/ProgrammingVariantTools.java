package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * The programming-exercise toolset for one agent round. One instance is created per round by
 * {@link ProgrammingVariantAdapterService#createTools}; it is NOT a Spring bean — it carries per-round state
 * (checked-out repositories, the touched-test-repo flag).
 *
 * All tools operate ONLY on the variant's repositories (never the source). Diff-style edits of existing files
 * ("transform, don't regenerate") are the main consistency lever, and validation errors (file not found,
 * ambiguous search text, unsafe path) are returned TO THE MODEL as the tool result.
 *
 * Cancellation: once the cancel flag is set, every tool short-circuits with an instruction to stop; the pipeline
 * performs the actual abort at the next round boundary, so cancellation never interrupts a running LLM call.
 */
class ProgrammingVariantTools implements VariantToolset {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingVariantTools.class);

    /**
     * The only path segment the agent may never write to. Everything else — build files included — must stay
     * editable: a domain re-theme has to rename the build's project name, or it fails the build it is judged by.
     */
    private static final String GIT_METADATA_SEGMENT = ".git";

    private static final int MAX_FILE_CONTENT_LENGTH = 100_000;

    /** Total character budget for one {@link #readFiles} call — bounds how much a single batch read can add to the round's context. */
    private static final int MAX_BATCH_READ_TOTAL_CHARS = 40_000;

    private static final int MAX_SEARCH_RESULTS = 50;

    private static final int MAX_SEARCH_LINE_LENGTH = 300;

    /** Per-round tool-call budget (see {@link VariantRoundBudget}); higher than the quiz budget — repo work needs more calls. */
    private static final int TOOL_CALL_BUDGET = 60;

    /** Character budget for {@link #prefetchContext}'s file-content section — bounds the prompt tokens it adds. */
    private static final int MAX_PREFETCH_CONTENT_CHARS = 30_000;

    /** Upper bound on distinct files prefetched, independent of the char budget — keeps the prefetch focused. */
    private static final int MAX_PREFETCH_FILES = 12;

    /** Matches backtick-quoted identifiers in a ChangePlan's intendedChanges, e.g. "rename `BankAccount` to `CargoBay`". */
    private static final Pattern BACKTICKED_IDENTIFIER = Pattern.compile("`([^`]+)`");

    /** A {@code <testid>} tag whose content is not a plain number — see {@link #normalizeTestIdReferences}. */
    private static final Pattern MALFORMED_TESTID = Pattern.compile("<testid>\\s*(?!\\d+\\s*</testid>)([^<]*?)\\s*</testid>");

    private final ProgrammingExercise exercise;

    private final User user;

    private final String jobId;

    private final ExerciseVariantJobService jobService;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    /** Runs one solution build so tests written this round become registered test cases (see {@link #listTestCases()}). */
    @FunctionalInterface
    interface SolutionBuildForTestDiscovery {

        void run(ProgrammingExercise exercise, String jobId);
    }

    private final SolutionBuildForTestDiscovery solutionBuildForTestDiscovery;

    private final String defaultBranch;

    /** The exercise this variant was generated from; used only by {@link #diffFile} to read the ORIGINAL file content. */
    private final ProgrammingExercise sourceExercise;

    private final Map<RepositoryType, Repository> checkouts = new EnumMap<>(RepositoryType.class);

    /** Read-only checkouts of the source exercise's repositories, resolved lazily. */
    private final Map<RepositoryType, Repository> sourceCheckouts = new EnumMap<>(RepositoryType.class);

    /** Source file paths per repository, resolved once per round, backing {@link #studentOwnedTemplateRefusal}. */
    private final Map<RepositoryType, Set<String>> sourcePaths = new EnumMap<>(RepositoryType.class);

    private volatile boolean touchedTestRepo;

    private volatile String finishSummary;

    private final ConcurrentHashMap<String, VariantJob.CallStat> toolCallStats = new ConcurrentHashMap<>();

    private final VariantRoundBudget budget;

    ProgrammingVariantTools(ProgrammingExercise exercise, User user, String jobId, ExerciseVariantJobService jobService, GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService, String defaultBranch,
            ProgrammingExercise sourceExercise, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            SolutionBuildForTestDiscovery solutionBuildForTestDiscovery) {
        this.exercise = exercise;
        this.user = user;
        this.jobId = jobId;
        this.jobService = jobService;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.sourceExercise = sourceExercise;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.solutionBuildForTestDiscovery = solutionBuildForTestDiscovery;
        this.defaultBranch = defaultBranch;
        this.budget = new VariantRoundBudget(TOOL_CALL_BUDGET, jobId, jobService);
    }

    @Override
    public List<ToolCallback> toolCallbacks() {
        return VariantToolset.withTiming(MethodToolCallbackProvider.builder().toolObjects(this).build().getToolCallbacks(), toolCallStats, budget::roundOver);
    }

    @Override
    public Map<String, VariantJob.CallStat> toolCallStats() {
        return Map.copyOf(toolCallStats);
    }

    @Override
    public boolean touchedTestRepo() {
        return touchedTestRepo;
    }

    @Override
    public String prefetchContext(ChangePlan plan) {
        StringBuilder context = new StringBuilder();
        for (RepositoryType repositoryType : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS)) {
            String tree = fileTree(repositoryType);
            if (tree != null) {
                context.append("=== ").append(repositoryType).append(" file tree ===\n").append(tree).append('\n');
            }
        }
        // Ground truth for the "template gains no implemented pieces" invariant: without it the agent cannot tell
        // which classes the source deliberately leaves for the student, and creates them in the variant TEMPLATE.
        String sourceTemplateTree = sourceFileTree(RepositoryType.TEMPLATE);
        if (sourceTemplateTree != null) {
            context.append("=== SOURCE TEMPLATE file tree (the exercise this variant is generated from) ===\n")
                    .append("These are the ONLY files the source template ships. Every other class the tests reference is written by the student and must have NO file in the "
                            + "variant's TEMPLATE either — renaming such a class does not give it a template file. The variant's TEMPLATE must hold the same set, modulo the "
                            + "renames the plan requires and any new given domain type the plan introduces.\n")
                    .append(sourceTemplateTree).append('\n');
        }
        appendPlannedFileContents(context, plan);
        return context.toString();
    }

    /** @return the sorted, newline-joined file paths of a repository, or {@code null} when it could not be read. */
    private String fileTree(RepositoryType repositoryType) {
        try {
            return String.join("\n", filePaths(checkout(repositoryType)));
        }
        catch (Exception e) {
            return null;
        }
    }

    /** {@link #fileTree(RepositoryType)} for the SOURCE exercise's repositories. */
    private String sourceFileTree(RepositoryType repositoryType) {
        try {
            return String.join("\n", filePaths(checkoutSource(repositoryType)));
        }
        catch (Exception e) {
            return null;
        }
    }

    /** @return the sorted repository-relative paths of every FILE (not directory) in the checkout. */
    private List<String> filePaths(Repository checkout) {
        return repositoryService.getFiles(checkout).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE).map(Map.Entry::getKey).sorted().toList();
    }

    /**
     * Appends the full content of files that plausibly need editing — those containing one of the backtick-quoted
     * identifiers in the plan's intendedChanges (the format the planning prompt uses, e.g. "rename `BankAccount`
     * to `CargoBay`") — bounded by {@link #MAX_PREFETCH_FILES} and {@link #MAX_PREFETCH_CONTENT_CHARS}. Falls back
     * to tree-only when the plan names no identifiers this way.
     */
    private void appendPlannedFileContents(StringBuilder context, ChangePlan plan) {
        Set<String> identifiers = extractBacktickedIdentifiers(plan.intendedChanges());
        if (identifiers.isEmpty()) {
            return;
        }
        int addedFiles = 0;
        int remainingBudget = MAX_PREFETCH_CONTENT_CHARS;
        outer: for (RepositoryType repositoryType : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS)) {
            Repository checkout;
            try {
                checkout = checkout(repositoryType);
            }
            catch (Exception e) {
                continue;
            }
            for (String path : filePaths(checkout)) {
                if (addedFiles >= MAX_PREFETCH_FILES) {
                    break outer;
                }
                String content;
                try {
                    content = new String(repositoryService.getFile(checkout, path), StandardCharsets.UTF_8);
                }
                catch (IOException e) {
                    continue;
                }
                if (identifiers.stream().noneMatch(content::contains)) {
                    continue;
                }
                if (content.length() > remainingBudget) {
                    context.append("\n[prefetch budget exhausted — further planned files omitted; read them with readFiles if needed]\n");
                    break outer;
                }
                context.append("\n=== ").append(repositoryType).append(":").append(path).append(" (prefetched — plan references an identifier found here) ===\n").append(content)
                        .append('\n');
                addedFiles++;
                remainingBudget -= content.length();
            }
        }
    }

    private static Set<String> extractBacktickedIdentifiers(List<String> intendedChanges) {
        return intendedChanges.stream().filter(Objects::nonNull).flatMap(change -> BACKTICKED_IDENTIFIER.matcher(change).results().map(match -> match.group(1).trim()))
                .filter(identifier -> !identifier.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
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
                // Losing the commit loses the round's work while verification passes on the stale pushed state.
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
            return String.join("\n", filePaths(checkout(repositoryType)));
        }
        catch (Exception e) {
            return "Error: could not list files of the " + repositoryType + " repository: " + e.getMessage();
        }
    }

    /** One read entry for the batch {@link #readFiles} tool, targeting its own repository. */
    public record FileRead(@JsonPropertyDescription("the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @JsonPropertyDescription("the file path relative to the repository root") String path) {
    }

    @Tool(description = "Read one or more files in a SINGLE call, each entry targeting any of TEMPLATE, SOLUTION, or TESTS — for example the template AND solution version "
            + "of the same file, or every file a rename touches, in one round trip. Pass a single entry for a one-off read. Each file's content is returned under its own "
            + "'repository:path' header; a missing file or read error is reported inline instead of failing the whole call. Bounded total size — if you hit the budget "
            + "notice, split the remaining files into another call.")
    public String readFiles(@ToolParam(description = "the files to read; each has repository and path") List<FileRead> reads) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        if (reads == null || reads.isEmpty()) {
            return "Error: no files were provided. Pass at least one { repository, path } entry.";
        }
        StringBuilder report = new StringBuilder();
        for (int index = 0; index < reads.size(); index++) {
            FileRead read = reads.get(index);
            report.append("--- ").append(read == null ? "?" : read.repository() + ":" + read.path()).append(" ---\n");
            if (read == null) {
                report.append("Error: the entry is missing.\n\n");
                continue;
            }
            RepositoryType repositoryType = parseRepositoryType(read.repository());
            if (repositoryType == null) {
                report.append(invalidRepositoryMessage(read.repository())).append("\n\n");
                continue;
            }
            try {
                Repository checkout = checkout(repositoryType);
                String content = new String(repositoryService.getFile(checkout, read.path()), StandardCharsets.UTF_8);
                if (content.length() > MAX_FILE_CONTENT_LENGTH) {
                    content = content.substring(0, MAX_FILE_CONTENT_LENGTH) + "\n[truncated]";
                }
                report.append(content).append("\n\n");
            }
            catch (IOException e) {
                report.append("Error: could not read file '").append(read.path()).append("' in the ").append(repositoryType).append(" repository: ").append(e.getMessage())
                        .append(". Use listFiles to see the existing file paths.\n\n");
            }
            catch (Exception e) {
                report.append("Error: could not access the ").append(repositoryType).append(" repository: ").append(e.getMessage()).append("\n\n");
            }
            if (report.length() > MAX_BATCH_READ_TOTAL_CHARS && index < reads.size() - 1) {
                report.append("[remaining file(s) omitted — total content exceeded the batch budget; read them in a separate readFiles call]");
                return report.toString();
            }
        }
        return report.toString();
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
            StringBuilder matches = new StringBuilder();
            int matchCount = 0;
            for (String path : filePaths(checkout)) {
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

    /** One search-and-replace edit for the batch {@link #applyEdits} tool, targeting its own repository. */
    public record BatchEdit(@JsonPropertyDescription("the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @JsonPropertyDescription("the file path relative to the repository root") String path,
            @JsonPropertyDescription("the exact text to search for; must match exactly one occurrence in the (current) file") String search,
            @JsonPropertyDescription("the replacement text") String replace) {
    }

    @Tool(description = "Apply one or more search-and-replace edits to EXISTING files in a SINGLE call, each targeting any of TEMPLATE, SOLUTION, or TESTS. "
            + "Gather every edit needed — including edits that span several repositories, for example a rename that touches the solution, template, AND test "
            + "repositories — and submit them all as one batch; pass a single entry for a one-off edit. Edits are applied IN ORDER and each sees the effect of the "
            + "previous ones in the same file. Each edit is reported independently as applied or with a precise error, and a failed edit never blocks the others — "
            + "only re-submit the failed ones in a follow-up call. Each edit's search text must occur exactly once in its current file. Every file is editable, "
            + "including build files (build.gradle, settings.gradle, pom.xml); only git's own .git directory is off limits.")
    public String applyEdits(@ToolParam(description = "the edits to apply in order; each has repository, path, search, and replace") List<BatchEdit> edits) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        if (edits == null || edits.isEmpty()) {
            return "Error: no edits were provided. Pass at least one { repository, path, search, replace } edit.";
        }
        // In-memory content per repository and touched file, so later edits see earlier ones (sequential
        // semantics, per file) and each file is read once and written once instead of once per edit.
        Map<RepositoryType, Map<String, String>> contentsByRepository = new EnumMap<>(RepositoryType.class);
        Map<RepositoryType, Set<String>> changedPathsByRepository = new EnumMap<>(RepositoryType.class);
        StringBuilder report = new StringBuilder();
        int appliedCount = 0;
        for (int index = 0; index < edits.size(); index++) {
            BatchEdit edit = edits.get(index);
            report.append("Edit ").append(index + 1).append(" (").append(edit == null ? "?" : edit.repository() + ":" + edit.path()).append("): ");
            if (edit == null) {
                report.append("Error: the edit entry is missing.\n");
                continue;
            }
            RepositoryType repositoryType = parseRepositoryType(edit.repository());
            if (repositoryType == null) {
                report.append(invalidRepositoryMessage(edit.repository())).append('\n');
                continue;
            }
            String normalizedPath = normalizeWritablePath(edit.path());
            if (normalizedPath == null) {
                report.append("Error: '").append(edit.path()).append("' is not an editable path (must be relative to the repo root, no '..', not in .git).\n");
                continue;
            }
            Repository checkout;
            try {
                checkout = checkout(repositoryType);
            }
            catch (Exception e) {
                report.append("Error: could not access the ").append(repositoryType).append(" repository: ").append(e.getMessage()).append('\n');
                continue;
            }
            Map<String, String> contents = contentsByRepository.computeIfAbsent(repositoryType, key -> new HashMap<>());
            String content = contents.get(normalizedPath);
            if (content == null) {
                try {
                    content = new String(repositoryService.getFile(checkout, normalizedPath), StandardCharsets.UTF_8);
                }
                catch (IOException e) {
                    report.append("Error: file '").append(edit.path()).append("' does not exist in the ").append(repositoryType)
                            .append(" repository. Use listFiles to see the existing file paths.\n");
                    continue;
                }
                contents.put(normalizedPath, content);
            }
            String search = edit.search();
            if (search == null || search.isEmpty()) {
                report.append("Error: the search text must not be empty for '").append(normalizedPath).append("'.\n");
                continue;
            }
            // Without this check the concatenation below would silently write the literal text "null" into the
            // file and still report success.
            if (edit.replace() == null) {
                report.append("Error: the replace text must not be null for '").append(normalizedPath).append("'. Use an empty string \"\" to delete the matched text.\n");
                continue;
            }
            int firstIndex = content.indexOf(search);
            if (firstIndex < 0) {
                report.append("Error: the search text was not found in '").append(normalizedPath).append("'. Read the file again and use the exact current text.\n");
                continue;
            }
            if (content.indexOf(search, firstIndex + 1) >= 0) {
                report.append("Error: the search text occurs more than once in '").append(normalizedPath)
                        .append("'. Extend the search text so it matches exactly one occurrence.\n");
                continue;
            }
            contents.put(normalizedPath, content.substring(0, firstIndex) + edit.replace() + content.substring(firstIndex + search.length()));
            changedPathsByRepository.computeIfAbsent(repositoryType, key -> new LinkedHashSet<>()).add(normalizedPath);
            appliedCount++;
            report.append("applied.\n");
        }
        for (Map.Entry<RepositoryType, Set<String>> entry : changedPathsByRepository.entrySet()) {
            RepositoryType repositoryType = entry.getKey();
            Repository checkout = checkouts.get(repositoryType);
            Map<String, String> contents = contentsByRepository.get(repositoryType);
            for (String path : entry.getValue()) {
                try {
                    writeFileContent(checkout, path, contents.get(path));
                }
                catch (IOException e) {
                    report.append("Error: could not write '").append(repositoryType).append(':').append(path).append("': ").append(e.getMessage()).append('\n');
                }
            }
            markTouched(repositoryType);
        }
        report.append(appliedCount).append(" of ").append(edits.size()).append(" edit(s) applied.");
        return report.toString();
    }

    /** One create-or-overwrite entry for the batch {@link #writeFiles} tool, targeting its own repository. */
    public record FileWrite(@JsonPropertyDescription("the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @JsonPropertyDescription("the file path relative to the repository root") String path, @JsonPropertyDescription("the full new file content") String content) {
    }

    @Tool(description = "Create new files (or fully overwrite existing ones) in a SINGLE call, each entry targeting any of TEMPLATE, SOLUTION, or TESTS — for example writing a "
            + "renamed build file in template and solution at once. Use this for NEW files, for complete rewrites of files the plan replaces wholesale, and for an EXISTING file "
            + "with many scattered changes where crafting unique applyEdits search text for every change would be awkward — a full-file rewrite is a fine choice there "
            + "too. Every path in a repository is writable except git's own .git directory. Each entry is reported independently as written or with a precise error.")
    public String writeFiles(@ToolParam(description = "the files to write; each has repository, path, and the full new content") List<FileWrite> writes) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        if (writes == null || writes.isEmpty()) {
            return "Error: no files were provided. Pass at least one { repository, path, content } entry.";
        }
        StringBuilder report = new StringBuilder();
        Set<RepositoryType> touchedRepositoryTypes = new LinkedHashSet<>();
        int appliedCount = 0;
        for (int index = 0; index < writes.size(); index++) {
            FileWrite write = writes.get(index);
            report.append("Entry ").append(index + 1).append(" (").append(write == null ? "?" : write.repository() + ":" + write.path()).append("): ");
            if (write == null) {
                report.append("Error: the entry is missing.\n");
                continue;
            }
            RepositoryType repositoryType = parseRepositoryType(write.repository());
            if (repositoryType == null) {
                report.append(invalidRepositoryMessage(write.repository())).append('\n');
                continue;
            }
            String normalizedPath = normalizeWritablePath(write.path());
            if (normalizedPath == null) {
                report.append(unwritablePathMessage(write.path(), repositoryType, "writable")).append('\n');
                continue;
            }
            try {
                Repository checkout = checkout(repositoryType);
                // Checked BEFORE the write: a student-owned class in the template is refused, not just warned about.
                boolean newTemplateFile = repositoryType == RepositoryType.TEMPLATE && gitService.getFileByName(checkout, normalizedPath).isEmpty();
                String refusal = newTemplateFile ? studentOwnedTemplateRefusal(normalizedPath) : null;
                if (refusal != null) {
                    report.append(refusal).append('\n');
                    continue;
                }
                writeFileContent(checkout, normalizedPath, write.content());
                touchedRepositoryTypes.add(repositoryType);
                appliedCount++;
                report.append("written.\n");
            }
            catch (Exception e) {
                report.append("Error: could not write to the ").append(repositoryType).append(" repository: ").append(e.getMessage()).append('\n');
            }
        }
        touchedRepositoryTypes.forEach(this::markTouched);
        report.append(appliedCount).append(" of ").append(writes.size()).append(" file(s) written.");
        return report.toString();
    }

    /**
     * Refuses creating a file in the TEMPLATE repository that holds a class the STUDENT is meant to write. Such a
     * template passes the structural tests it is required to fail, so the "template scores exactly 0%" gate can
     * never go green however many repair rounds follow — unrecoverable within the attempt budget, and it kept
     * happening with the rule stated in the system prompt, so it is enforced rather than advised.
     * <p>
     * The discriminator needs no LLM judgement: a student-owned class is one the SOURCE SOLUTION has and the
     * SOURCE TEMPLATE deliberately lacks. A new given domain type the plan introduces exists in NEITHER source
     * repository and is still allowed through, which is why this cannot simply reject every new template file.
     *
     * @param path the normalized repository-relative path the round is trying to create in the template
     * @return the refusal to report for this entry, or {@code null} when the write is legitimate
     */
    private String studentOwnedTemplateRefusal(String path) {
        if (!sourcePaths(RepositoryType.SOLUTION).contains(path) || sourcePaths(RepositoryType.TEMPLATE).contains(path)) {
            return null;
        }
        return "REFUSED: '" + path + "' exists in the source SOLUTION but deliberately NOT in the source TEMPLATE — it is a class the student has to write from scratch, so the "
                + "variant's template must not contain it either. Creating it makes the template pass the structural tests it is required to fail, which no later repair round "
                + "can undo. Nothing was written. Apply this change to the SOLUTION and TESTS repositories only; in the template, reflect it in Client.java's TODO comments.";
    }

    /** The source exercise's file paths, resolved once per round and repository. */
    private Set<String> sourcePaths(RepositoryType repositoryType) {
        return sourcePaths.computeIfAbsent(repositoryType, type -> {
            String tree = sourceFileTree(type);
            // Fails open: an unreadable source repository yields an empty set, so nothing is classified
            // student-owned — never a blanket block on every new template file.
            return tree == null ? Set.of() : Set.copyOf(List.of(tree.split("\n")));
        });
    }

    /** One delete entry for the batch {@link #deleteFiles} tool, targeting its own repository. */
    public record FileDelete(@JsonPropertyDescription("the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @JsonPropertyDescription("the file path relative to the repository root") String path) {
    }

    @Tool(description = "Delete existing files in a SINGLE call, each entry targeting any of TEMPLATE, SOLUTION, or TESTS. Only use this for files the plan removes "
            + "(e.g. a test class dropped when making the exercise easier). Every path is deletable except git's own .git directory. Each entry is reported independently.")
    public String deleteFiles(@ToolParam(description = "the files to delete; each has repository and path") List<FileDelete> deletes) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        if (deletes == null || deletes.isEmpty()) {
            return "Error: no files were provided. Pass at least one { repository, path } entry.";
        }
        StringBuilder report = new StringBuilder();
        Set<RepositoryType> touchedRepositoryTypes = new LinkedHashSet<>();
        int appliedCount = 0;
        for (int index = 0; index < deletes.size(); index++) {
            FileDelete delete = deletes.get(index);
            report.append("Entry ").append(index + 1).append(" (").append(delete == null ? "?" : delete.repository() + ":" + delete.path()).append("): ");
            if (delete == null) {
                report.append("Error: the entry is missing.\n");
                continue;
            }
            RepositoryType repositoryType = parseRepositoryType(delete.repository());
            if (repositoryType == null) {
                report.append(invalidRepositoryMessage(delete.repository())).append('\n');
                continue;
            }
            String normalizedPath = normalizeWritablePath(delete.path());
            if (normalizedPath == null) {
                report.append(unwritablePathMessage(delete.path(), repositoryType, "deletable")).append('\n');
                continue;
            }
            try {
                Repository checkout = checkout(repositoryType);
                if (gitService.getFileByName(checkout, normalizedPath).isEmpty()) {
                    report.append("Error: file '").append(delete.path()).append("' does not exist in the ").append(repositoryType)
                            .append(" repository. Use listFiles to see the existing file paths.\n");
                    continue;
                }
                repositoryService.deleteFile(checkout, normalizedPath);
                touchedRepositoryTypes.add(repositoryType);
                appliedCount++;
                report.append("deleted.\n");
            }
            catch (Exception e) {
                report.append("Error: could not delete from the ").append(repositoryType).append(" repository: ").append(e.getMessage()).append('\n');
            }
        }
        touchedRepositoryTypes.forEach(this::markTouched);
        report.append(appliedCount).append(" of ").append(deletes.size()).append(" file(s) deleted.");
        return report.toString();
    }

    @Tool(description = "List the exact names of every test case Artemis has registered for this exercise. Call this before writing or updating problem-statement task markers so "
            + "every test name you reference is exact — a stale or typo'd name silently drops the task-test link instead of erroring. Cost: test cases are discovered from build "
            + "results, so if you edited the test repository since the last build, this call runs a full build (tens of seconds) to discover the real names. Finish your "
            + "test-repository changes first, then call this once and write every task marker from that single result. It is instant when you have not touched the test "
            + "repository since the last build, so calling it before you start editing costs nothing.")
    public String listTestCases() {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        // Test cases are only discovered from build results, so tests written this round are invisible here until
        // a build has executed them. A guessed name silently unlinks its task from grading, so commit and run one
        // solution build instead — but ONLY when the test repository actually changed since the last one.
        if (touchedTestRepo) {
            flushPendingChanges();
            solutionBuildForTestDiscovery.run(exercise, jobId);
            touchedTestRepo = false;
        }
        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseId(exercise.getId());
        if (testCases.isEmpty()) {
            return "No test cases are registered yet for this exercise. Test cases are discovered from build results — run a build first.";
        }
        List<String> names = testCases.stream().map(ProgrammingExerciseTestCase::getTestName).sorted().toList();
        // Framed as "the complete set, copy verbatim" rather than as a bare list: several names are generated per
        // member (testClass[SortStrategy]) in a set that is deliberately not symmetric, and agents were observed
        // rewriting them into tidier-looking names, which silently unlinks the task from grading.
        return "These " + names.size() + " names are the complete set of tests that exist, and the only strings a task marker may reference. "
                + "Copy one character for character — no parentheses, no parameter list, no rewording — or the task loses its grading link:\n" + String.join("\n", names);
    }

    @Tool(description = "Replace the variant exercise's problem statement (Markdown). Call this last, after the final test names are settled, "
            + "so every test referenced in the tasks actually exists in the test repository. Task markers reference tests by name: "
            + "\"[task][Implement Bubble Sort](testBubbleSort)\", several separated by commas. Each name must be copied character for character from "
            + "listTestCases — no parentheses, no parameter list, and never reworded to read better: many names are generated per member and look "
            + "like \"testClass[SortStrategy]\", which cannot be guessed from the class name. This call reports any reference that does not match a "
            + "real test, together with the names that do exist. Never write a <testid> tag yourself — Artemis converts names to ids on its own. "
            + "A <testid> tag may only ever contain a number, so wrapping a name in one (<testid>testBubbleSort</testid>) resolves to nothing and "
            + "silently unlinks that task from grading.")
    public String updateProblemStatement(
            @ToolParam(description = "the full new problem statement in Artemis Markdown; task markers reference tests by plain name") String problemStatement) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        if (problemStatement == null || problemStatement.isBlank()) {
            return "Error: the problem statement must not be empty.";
        }
        try {
            // Repair the one malformed shape that is both common and silently fatal (see normalizeTestIdReferences).
            String normalized = normalizeTestIdReferences(problemStatement);
            boolean repaired = !normalized.equals(problemStatement);
            ProgrammingExercise persisted = programmingExerciseRepository.findByIdElseThrow(exercise.getId());
            persisted.setProblemStatement(ProgrammingVariantAdapterService.stripPlantUmlCodeFences(normalized));
            programmingExerciseRepository.save(persisted);
            // Keep the task/test-case mapping in sync, exactly like the regular problem-statement update endpoint.
            programmingExerciseTaskService.updateTasksFromProblemStatement(persisted);
            exercise.setProblemStatement(persisted.getProblemStatement());
            String message = repaired
                    ? "Problem statement updated. Note: task markers contained <testid> tags wrapping test names — a <testid> tag may only contain a numeric id, so those "
                            + "would have unlinked their tasks from grading. They were rewritten to plain test names for you. Reference tests by plain name; never emit <testid> yourself."
                    : "Problem statement updated.";
            return message + unresolvedReferenceReport(persisted);
        }
        catch (Exception e) {
            return "Error: could not update the problem statement: " + e.getMessage();
        }
    }

    /**
     * Reports task-marker references that match no real test case, together with the names that do exist. The same
     * check runs later as the TEST_REFERENCES verification gate, but only after a full build round and only when
     * the build gate is already clean — reporting it here closes the loop where the mistake is made. Deliberately
     * not an error: the statement is saved either way, since a partially-linked statement is better than none.
     *
     * @param exercise the freshly persisted variant exercise
     * @return an empty string when everything resolves, otherwise a correction notice
     */
    private String unresolvedReferenceReport(ProgrammingExercise exercise) {
        List<String> unresolved;
        try {
            unresolved = programmingExerciseTaskService.findUnresolvedTaskTestReferences(exercise);
        }
        catch (Exception e) {
            return "";
        }
        if (unresolved.isEmpty()) {
            return "";
        }
        String available = programmingExerciseTestCaseRepository.findByExerciseId(exercise.getId()).stream().map(ProgrammingExerciseTestCase::getTestName).sorted()
                .collect(Collectors.joining(", "));
        return " WARNING: these task-marker references match no test and are therefore not linked to grading: " + String.join(", ", unresolved)
                + ". The only names that exist are [" + available + "]. Copy one of them character for character — do not reword it, and do not add parentheses. "
                + "If none of them covers the requirement, remove the reference rather than inventing a name, then call updateProblemStatement again.";
    }

    /**
     * Unwraps {@code <testid>} tags whose content is not a numeric test-case id, keeping the inner text. Artemis
     * resolves a task's test reference either by plain NAME or, inside {@code <testid>}, by parsing the content as
     * a numeric id. Models reproduce the stored {@code <testid>27</testid>} syntax but fill in the test NAME, and
     * {@code <testid>testBubbleSort()</testid>} matches neither branch — the task silently loses its grading link.
     * The rewrite is unambiguous (a non-numeric payload is never a valid id) and yields the plain-name form
     * Artemis converts back to ids on its own.
     *
     * @param problemStatement the statement as written by the model
     * @return the statement with non-numeric {@code <testid>} wrappers removed
     */
    static String normalizeTestIdReferences(String problemStatement) {
        return MALFORMED_TESTID.matcher(problemStatement).replaceAll(matchResult -> Matcher.quoteReplacement(matchResult.group(1).strip()));
    }

    @Tool(description = "Show a unified diff between a file's ORIGINAL content in the SOURCE exercise this variant was generated from and its CURRENT content in this variant. "
            + "Use this to sanity-check your own changes — especially after writeFiles rewrites a whole file — and confirm the diff is limited to what the plan actually requires, "
            + "not an unexpectedly large or unrelated change.")
    public String diffFile(@ToolParam(description = "the repository: TEMPLATE, SOLUTION, or TESTS") String repository,
            @ToolParam(description = "the file path relative to the repository root") String path) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        RepositoryType repositoryType = parseRepositoryType(repository);
        if (repositoryType == null) {
            return invalidRepositoryMessage(repository);
        }
        byte[] sourceBytes;
        try {
            Repository sourceCheckout = checkoutSource(repositoryType);
            sourceBytes = repositoryService.getFile(sourceCheckout, path);
        }
        catch (IOException e) {
            sourceBytes = null;
        }
        catch (Exception e) {
            return "Error: could not access the source " + repositoryType + " repository: " + e.getMessage();
        }
        byte[] variantBytes;
        try {
            Repository checkout = checkout(repositoryType);
            variantBytes = repositoryService.getFile(checkout, path);
        }
        catch (IOException e) {
            variantBytes = null;
        }
        catch (Exception e) {
            return "Error: could not access the " + repositoryType + " repository: " + e.getMessage();
        }
        if (sourceBytes == null && variantBytes == null) {
            return "Error: file '" + path + "' does not exist in either the source or the variant " + repositoryType + " repository.";
        }
        if (sourceBytes == null) {
            return "'" + path + "' does not exist in the SOURCE " + repositoryType + " repository (new file introduced in this variant) — nothing to diff against.";
        }
        if (variantBytes == null) {
            return "'" + path + "' no longer exists in the variant " + repositoryType + " repository (deleted).";
        }
        RawText sourceText = new RawText(sourceBytes);
        RawText variantText = new RawText(variantBytes);
        EditList edits = new EditList();
        edits.addAll(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS).diff(RawTextComparator.DEFAULT, sourceText, variantText));
        if (edits.isEmpty()) {
            return "No differences — '" + path + "' is unchanged from the source in the " + repositoryType + " repository.";
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setContext(3);
                formatter.format(edits, sourceText, variantText);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            return "Error: could not render the diff for '" + path + "': " + e.getMessage();
        }
    }

    // returnDirect ends the internal tool loop immediately, so the "budget exhausted, call finish" directive has a
    // guaranteed exit and no extra LLM round follows.
    @Tool(returnDirect = true, description = "Signal that you are done with this round and provide a short summary of what you changed and verified.")
    public String finish(@ToolParam(description = "a short summary of the changes made in this round") String summary) {
        this.finishSummary = summary;
        return "Summary recorded. You are done with this round.";
    }

    /** Cancellation / tool-budget check every tool except finish short-circuits on; see {@link VariantRoundBudget}. */
    private String stopNotice() {
        return budget.stopNotice();
    }

    private Repository checkout(RepositoryType repositoryType) throws GitAPIException {
        return checkout(exercise, checkouts, repositoryType, "");
    }

    /** Read-only counterpart of {@link #checkout} resolving the SOURCE exercise's repositories. */
    private Repository checkoutSource(RepositoryType repositoryType) throws GitAPIException {
        return checkout(sourceExercise, sourceCheckouts, repositoryType, "source ");
    }

    private Repository checkout(ProgrammingExercise checkedOutExercise, Map<RepositoryType, Repository> cache, RepositoryType repositoryType, String label) throws GitAPIException {
        Repository cached = cache.get(repositoryType);
        if (cached != null) {
            return cached;
        }
        LocalVCRepositoryUri repositoryUri = checkedOutExercise.getRepositoryURI(repositoryType);
        if (repositoryUri == null) {
            throw new IllegalStateException("No " + repositoryType + " repository URI for " + label + "exercise " + checkedOutExercise.getId());
        }
        Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true, defaultBranch, false);
        if (repository == null) {
            throw new IllegalStateException("Could not check out the " + label + repositoryType + " repository");
        }
        cache.put(repositoryType, repository);
        return repository;
    }

    private static String truncateLine(String line) {
        return line.length() > MAX_SEARCH_LINE_LENGTH ? line.substring(0, MAX_SEARCH_LINE_LENGTH) + " [truncated]" : line;
    }

    private void writeFileContent(Repository repository, String path, String content) throws IOException {
        // createFile rejects an existing path, so an overwrite has to delete first. Keep the previous bytes:
        // otherwise a failed write leaves the file deleted and the agent's next round sees a missing file.
        byte[] previousContent = null;
        if (gitService.getFileByName(repository, path).isPresent()) {
            previousContent = repositoryService.getFile(repository, path);
            repositoryService.deleteFile(repository, path);
        }
        try {
            repositoryService.createFile(repository, path, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        }
        catch (RuntimeException | IOException e) {
            if (previousContent != null) {
                if (gitService.getFileByName(repository, path).isPresent()) {
                    repositoryService.deleteFile(repository, path);
                }
                repositoryService.createFile(repository, path, new ByteArrayInputStream(previousContent));
            }
            throw e;
        }
    }

    private void markTouched(RepositoryType repositoryType) {
        if (repositoryType == RepositoryType.TESTS) {
            // A test-repo change invalidates the previous round's build evidence for BOTH builds.
            touchedTestRepo = true;
        }
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
     * corrupt its git metadata; every remaining path inside the repository is writable.
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
                // Case-insensitive: on macOS/Windows ".GIT" resolves to the same directory as ".git".
                if (GIT_METADATA_SEGMENT.equalsIgnoreCase(segment)) {
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
