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
 * {@link ProgrammingVariantAdapters#createTools}; it is NOT a Spring bean — it carries per-round state
 * (checked-out repositories, the touched-test-repo flag).
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

    /** Total character budget for one {@link #readFiles} call — bounds how much a single batch read can add to the round's context. */
    private static final int MAX_BATCH_READ_TOTAL_CHARS = 40_000;

    private static final int MAX_SEARCH_RESULTS = 50;

    private static final int MAX_SEARCH_LINE_LENGTH = 300;

    /** Per-round tool-call budget (see {@link #stopNotice()}); higher than the quiz budget — repo work needs more calls. */
    private static final int TOOL_CALL_BUDGET = 60;

    /**
     * Total character budget for {@link #prefetchContext}'s file-content section (performance lever A4): bounds
     * how many extra prompt tokens the prefetch can add, so cutting the agent's blind opening reads never turns
     * into an unbounded token-cost regression on a large exercise.
     */
    private static final int MAX_PREFETCH_CONTENT_CHARS = 30_000;

    /** Upper bound on distinct files prefetched, independent of the char budget — keeps the prefetch focused. */
    private static final int MAX_PREFETCH_FILES = 12;

    /** Matches backtick-quoted identifiers in a ChangePlan's intendedChanges, e.g. "rename `BankAccount` to `CargoBay`". */
    private static final Pattern BACKTICKED_IDENTIFIER = Pattern.compile("`([^`]+)`");

    private final ProgrammingExercise exercise;

    private final User user;

    private final String jobId;

    private final ExerciseVariantJobService jobService;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final String defaultBranch;

    /** The exercise this variant was generated from; used only by {@link #diffFile} to read the ORIGINAL file content. */
    private final ProgrammingExercise sourceExercise;

    private final Map<RepositoryType, Repository> checkouts = new EnumMap<>(RepositoryType.class);

    /** Read-only checkouts of the source exercise's repositories, lazily resolved by {@link #diffFile}. */
    private final Map<RepositoryType, Repository> sourceCheckouts = new EnumMap<>(RepositoryType.class);

    private boolean touchedTestRepo;

    private String finishSummary;

    private int toolCallsUsed;

    private final ConcurrentHashMap<String, VariantJob.CallStat> toolCallStats = new ConcurrentHashMap<>();

    ProgrammingVariantTools(ProgrammingExercise exercise, User user, String jobId, ExerciseVariantJobService jobService, GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService, String defaultBranch,
            ProgrammingExercise sourceExercise, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository) {
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
        this.defaultBranch = defaultBranch;
    }

    @Override
    public List<ToolCallback> toolCallbacks() {
        return VariantToolset.withTiming(MethodToolCallbackProvider.builder().toolObjects(this).build().getToolCallbacks(), toolCallStats);
    }

    @Override
    public Map<String, VariantJob.CallStat> toolCallStats() {
        return Map.copyOf(toolCallStats);
    }

    @Override
    public boolean touchedTestRepo() {
        return touchedTestRepo;
    }

    // Performance lever A4: the transform agent otherwise starts every round blind and spends its first several
    // calls on listFiles/readFiles just to see what it's working with — a ChatClient call has no memory of a
    // previous round's reads (each round is a fresh conversation), so this cost repeats on every repair round too.
    @Override
    public String prefetchContext(ChangePlan plan) {
        StringBuilder context = new StringBuilder();
        for (RepositoryType repositoryType : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS)) {
            String tree = fileTree(repositoryType);
            if (tree != null) {
                context.append("=== ").append(repositoryType).append(" file tree ===\n").append(tree).append('\n');
            }
        }
        appendPlannedFileContents(context, plan);
        return context.toString();
    }

    /** @return the sorted, newline-joined file paths of a repository, or {@code null} when it could not be read. */
    private String fileTree(RepositoryType repositoryType) {
        try {
            Repository checkout = checkout(repositoryType);
            return repositoryService.getFiles(checkout).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE).map(Map.Entry::getKey).sorted()
                    .collect(Collectors.joining("\n"));
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * Appends the full content of files that plausibly need editing — every file across the three repositories
     * whose content contains one of the backtick-quoted identifiers in the plan's intendedChanges (the format the
     * planning prompt is instructed to use, e.g. "rename `BankAccount` to `CargoBay`") — bounded by
     * {@link #MAX_PREFETCH_FILES} and {@link #MAX_PREFETCH_CONTENT_CHARS}. Falls back to tree-only (no content
     * section) when the plan names no identifiers this way, or the budget is exhausted before a file is added.
     */
    private void appendPlannedFileContents(StringBuilder context, ChangePlan plan) {
        Set<String> identifiers = extractBacktickedIdentifiers(plan.intendedChanges());
        if (identifiers.isEmpty()) {
            return;
        }
        Set<String> alreadyAdded = new LinkedHashSet<>();
        int remainingBudget = MAX_PREFETCH_CONTENT_CHARS;
        outer: for (RepositoryType repositoryType : List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS)) {
            Repository checkout;
            try {
                checkout = checkout(repositoryType);
            }
            catch (Exception e) {
                continue;
            }
            List<String> paths = repositoryService.getFiles(checkout).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE).map(Map.Entry::getKey).sorted()
                    .toList();
            for (String path : paths) {
                String key = repositoryType + ":" + path;
                if (alreadyAdded.contains(key) || alreadyAdded.size() >= MAX_PREFETCH_FILES) {
                    continue;
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
                alreadyAdded.add(key);
                remainingBudget -= content.length();
            }
        }
    }

    private static Set<String> extractBacktickedIdentifiers(List<String> intendedChanges) {
        Set<String> identifiers = new LinkedHashSet<>();
        for (String change : intendedChanges) {
            if (change == null) {
                continue;
            }
            Matcher matcher = BACKTICKED_IDENTIFIER.matcher(change);
            while (matcher.find()) {
                String identifier = matcher.group(1).trim();
                if (!identifier.isBlank()) {
                    identifiers.add(identifier);
                }
            }
        }
        return identifiers;
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

    /**
     * One search-and-replace edit for the batch {@link #applyEdits} tool, targeting its own repository so a batch
     * can span all three repositories in one call instead of one call per repository.
     */
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
        // In-memory content per repository and touched file so later edits see earlier ones (sequential
        // semantics, per file) and each file is read once and written once, instead of a read+delete+create per
        // edit. Repository checkouts are resolved lazily and cached, same as every other tool in this round.
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
            EditOutcome outcome = applySearchReplace(content, normalizedPath, edit.search(), edit.replace());
            if (outcome.failed()) {
                report.append("Error: ").append(outcome.error()).append('\n');
                continue;
            }
            contents.put(normalizedPath, outcome.updatedContent());
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

    /**
     * Applies a single search-and-replace to {@code content} without touching the repository — one definition of
     * a valid edit (unique-match requirement, error wording) shared by every entry the batch {@link #applyEdits}
     * tool applies. Returns the updated content or a precise error.
     */
    private static EditOutcome applySearchReplace(String content, String path, String search, String replace) {
        if (search == null || search.isEmpty()) {
            return EditOutcome.error("the search text must not be empty for '" + path + "'.");
        }
        if (replace == null) {
            // Without this check, string concatenation below would silently turn a null replace into the
            // literal 4-character text "null" in the file instead of erroring — a real, silent corruption
            // observed only by code review, never by a failing test, since the tool would still report success.
            return EditOutcome.error("the replace text must not be null for '" + path + "'. Use an empty string \"\" to delete the matched text.");
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

    @Tool(description = "List the exact names of every test case Artemis currently has registered for this exercise (discovered from build results). Call this before writing or "
            + "updating problem-statement task markers so every test name you reference is exact — a stale or typo'd name silently drops the task-test link instead of erroring.")
    public String listTestCases() {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseId(exercise.getId());
        if (testCases.isEmpty()) {
            return "No test cases are registered yet for this exercise. Test cases are discovered from build results — run a build first.";
        }
        return testCases.stream().map(ProgrammingExerciseTestCase::getTestName).sorted().collect(Collectors.joining("\n"));
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

    /** Read-only counterpart of {@link #checkout} resolving the SOURCE exercise's repositories, for {@link #diffFile}. */
    private Repository checkoutSource(RepositoryType repositoryType) throws GitAPIException {
        Repository cached = sourceCheckouts.get(repositoryType);
        if (cached != null) {
            return cached;
        }
        LocalVCRepositoryUri repositoryUri = sourceExercise.getRepositoryURI(repositoryType);
        if (repositoryUri == null) {
            throw new IllegalStateException("No " + repositoryType + " repository URI for source exercise " + sourceExercise.getId());
        }
        Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true, defaultBranch, false);
        if (repository == null) {
            throw new IllegalStateException("Could not check out the source " + repositoryType + " repository");
        }
        sourceCheckouts.put(repositoryType, repository);
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
            // A test-repo change invalidates the previous round's build evidence (build-dependency constraint):
            // the VERIFYING gate always re-triggers both builds regardless, but this flag tells the pipeline to
            // treat both as unverified even if this round never itself checked them.
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
                // Case-insensitive: on a case-insensitive filesystem (macOS default, Windows) ".GIT"/".Git"
                // resolves to the same directory as ".git" and must be blocked the same way.
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
