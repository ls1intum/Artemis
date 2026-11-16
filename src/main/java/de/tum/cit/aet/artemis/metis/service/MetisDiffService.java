package de.tum.cit.aet.artemis.metis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.SET_UP_TEMPLATE_FOR_EXERCISE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.programming.exception.GitException;

/**
 * Computes the code a student wrote themselves: the diff of their repository at the submitted commit against the template their participation started from.
 * <p>
 * The diff starts at the commit in the history of the submitted commit that set up the template, or at its root commit when no commit did. A changed file
 * counts as own code if it still exists at the submitted commit, is a regular text file of at most 512 KiB whose path fits the focus file column and contains no
 * line break or control character, and is not left out by {@link MetisChangedFileFilter} (metadata, build files, tests, documentation). Whitespace-only edits
 * and blank lines do not count, and a renamed template file counts with its edits only. The files with enough changed lines become the focus candidates of
 * the questions.
 * <p>
 * The repositories are the bare repositories of the local version control server. They are only read, never checked out.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisDiffService {

    private static final Logger log = LoggerFactory.getLogger(MetisDiffService.class);

    /** Files above this size are generated data or vendored code rather than own code; they are never loaded into memory. */
    static final int MAX_FILE_BYTES = 512 * 1024;

    /**
     * The width of the column that stores the path of a focus file, {@code metis_question.focus_file_path}. A nested path can be longer than 255 characters with
     * every segment valid, and such a file is own code like any other; only a path the column cannot hold is left out.
     */
    static final int MAX_PATH_LENGTH = 1024;

    /**
     * The most changed files one diff keeps. A student's own code spans a few dozen files; a repository with more has generated or vendored files committed.
     * Together with the cut of every file to the size a prompt takes, the limit bounds the memory a diff holds.
     */
    static final int MAX_CHANGED_FILES = 500;

    /** Unchanged lines around each change in a diff. */
    private static final int DIFF_CONTEXT_LINES = 3;

    /** Starts every hunk of a diff; the lines before the first hunk are the file header. */
    private static final String HUNK_START = "@@ ";

    private final BareGitRepositoryService bareGitRepositoryService;

    private final int minChangedLines;

    private final int maxFocusFileCharacters;

    /**
     * One changed file of the student's own code. Content and diff are already cut to the size a prompt takes, like those of a {@link FocusFileContext}.
     *
     * @param path          the path of the file at the submitted commit
     * @param insertedLines the lines the student added, without blank lines
     * @param deletedLines  the lines the student removed, without blank lines
     * @param content       the full content of the file at the submitted commit, or null if it is longer than {@code max-focus-file-characters} (a prompt then
     *                          takes the diff only)
     * @param unifiedDiff   the hunks of the file against the template with three lines of context, without the file header, cut to
     *                          {@code max-focus-file-characters}
     */
    public record FileChange(String path, int insertedLines, int deletedLines, @Nullable String content, String unifiedDiff) {

        /**
         * The size of the student's change to this file, as stored with a focus file.
         *
         * @return the added plus the removed lines
         */
        public int changedLines() {
            return insertedLines + deletedLines;
        }

        /**
         * Describes the file without its code, so that student code never reaches a log line through this record.
         *
         * @return the path and the line counts
         */
        @Override
        public String toString() {
            return "FileChange[path=" + path + ", insertedLines=" + insertedLines + ", deletedLines=" + deletedLines + "]";
        }
    }

    /**
     * The own code of a student at one commit.
     *
     * @param baseCommitHash   the commit the diff starts at: the setup commit of the template, or the root commit if the repository has none
     * @param targetCommitHash the submitted commit
     * @param setupCommitFound whether the base is a setup commit; false if the diff starts at the root commit
     * @param changedFiles     every changed file that counts as own code, sorted by path
     * @param focusCandidates  the changed files with at least {@code min-changed-lines} changed lines, or all of them if none has that many; empty without own code
     */
    public record OwnCodeDiff(String baseCommitHash, String targetCommitHash, boolean setupCommitFound, List<FileChange> changedFiles, List<FileChange> focusCandidates) {

        /**
         * Keeps both file lists unmodifiable.
         */
        public OwnCodeDiff {
            changedFiles = List.copyOf(changedFiles);
            focusCandidates = List.copyOf(focusCandidates);
        }
    }

    /**
     * The focus file of a question, read again for a rating or a regeneration. Content and diff are already cut to the size a prompt takes.
     *
     * @param path         the path of the file
     * @param content      the full content at the commit, or null if it is longer than {@code max-focus-file-characters} (a prompt then takes the diff only)
     * @param unifiedDiff  the hunks against the template, cut to {@code max-focus-file-characters}, or null if the file did not change
     * @param changedLines the added plus the removed lines, without blank lines; 0 if the file did not change
     */
    public record FocusFileContext(String path, @Nullable String content, @Nullable String unifiedDiff, int changedLines) {

        /**
         * Describes the file without its code, so that student code never reaches a log line through this record.
         *
         * @return the path and the number of changed lines
         */
        @Override
        public String toString() {
            return "FocusFileContext[path=" + path + ", changedLines=" + changedLines + "]";
        }
    }

    /**
     * The student's repository cannot be used: its URI is invalid, it does not exist or cannot be read, or it does not contain the requested commit.
     */
    public static class RepositoryUnavailableException extends RuntimeException {

        /**
         * Creates the exception without an underlying cause.
         *
         * @param message what cannot be used
         */
        public RepositoryUnavailableException(String message) {
            super(message);
        }

        /**
         * Creates the exception for an underlying failure.
         *
         * @param message what cannot be used
         * @param cause   the failure that made the repository unusable
         */
        public RepositoryUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public MetisDiffService(BareGitRepositoryService bareGitRepositoryService, MetisProperties properties) {
        this.bareGitRepositoryService = bareGitRepositoryService;
        this.minChangedLines = properties.generation().minChangedLines();
        this.maxFocusFileCharacters = properties.generation().maxFocusFileCharacters();
    }

    /**
     * Computes the own code of a student: every changed file of the repository at the given commit against the template, with its added and removed lines,
     * and the files that qualify as focus files.
     *
     * @param repositoryUri    the URI of the student's repository on the local version control server
     * @param targetCommitHash the full hash of the submitted commit
     * @return the own code, whose focus candidates are empty if the student wrote none
     * @throws RepositoryUnavailableException if the URI is invalid, or the repository cannot be opened or read, or does not contain the commit
     */
    public OwnCodeDiff computeOwnCodeDiff(String repositoryUri, String targetCommitHash) {
        try (Repository repository = openRepository(repositoryUri)) {
            String baseCommitHash = findBaseCommit(repository, commitId(targetCommitHash));
            return computeDiff(repository, baseCommitHash, targetCommitHash);
        }
        catch (RepositoryUnavailableException e) {
            log.warn("Metis cannot compute the own code of commit {} in the repository {}: {}", targetCommitHash, repositoryUri, e.getMessage());
            throw e;
        }
        catch (IOException e) {
            log.warn("Metis cannot compute the own code of commit {} in the repository {}: {}", targetCommitHash, repositoryUri, e.getMessage());
            throw new RepositoryUnavailableException("Cannot read commit " + targetCommitHash + " of the repository " + repositoryUri, e);
        }
    }

    /**
     * Reads the focus file of a question again: its content at the commit and its diff against the template, computed like the diff of
     * {@link #computeOwnCodeDiff}, so that a renamed template file shows its edits only. Content and diff are cut to the size a prompt takes (see
     * {@link FocusFileContext}).
     *
     * @param repositoryUri the URI of the student's repository, may be null
     * @param commitHash    the full hash of the commit the questions were generated from, may be null
     * @param path          the path of the focus file, may be null
     * @return the focus file, or empty if an argument is missing, the repository cannot be read, or the file is missing, binary or larger than 512 KiB
     */
    public Optional<FocusFileContext> readFocusFileContext(@Nullable String repositoryUri, @Nullable String commitHash, @Nullable String path) {
        if (repositoryUri == null || commitHash == null || path == null || path.isBlank()) {
            return Optional.empty();
        }
        try (Repository repository = openRepository(repositoryUri)) {
            String baseCommitHash = findBaseCommit(repository, commitId(commitHash));
            return readFocusFile(repository, baseCommitHash, commitHash, path);
        }
        catch (RepositoryUnavailableException | IOException | IllegalArgumentException e) {
            log.warn("Metis cannot read the focus file {} of commit {} in the repository {}: {}", path, commitHash, repositoryUri, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Picks the focus file of each question: the focus candidates in a random order, starting over when there are fewer candidates than questions, so that a
     * file is then asked about more than once. The choice is uniform among the candidates and reproducible for a seeded generator.
     *
     * @param diff   the own code of the student
     * @param count  the number of questions
     * @param random the source of randomness, seeded with the check id for a reproducible choice
     * @return the focus file of question 1 to {@code count}, in this order; empty if there is no candidate
     */
    public static List<FileChange> pickFocusFiles(OwnCodeDiff diff, int count, RandomGenerator random) {
        List<FileChange> candidates = new ArrayList<>(diff.focusCandidates());
        if (candidates.isEmpty() || count <= 0) {
            return List.of();
        }
        Collections.shuffle(candidates, random);
        List<FileChange> focusFiles = new ArrayList<>(count);
        for (int question = 0; question < count; question++) {
            focusFiles.add(candidates.get(question % candidates.size()));
        }
        return List.copyOf(focusFiles);
    }

    /**
     * Computes the own code between two commits of a repository. A hash that is not a full commit hash throws a {@link RepositoryUnavailableException}. Keeps
     * at most {@link #MAX_CHANGED_FILES} changed files.
     *
     * @param repository       the repository, opened by the caller
     * @param baseCommitHash   the full hash of the commit the diff starts at
     * @param targetCommitHash the full hash of the submitted commit
     * @return the own code between the two commits
     * @throws IOException if the repository cannot be read or does not contain one of the commits
     */
    OwnCodeDiff computeDiff(Repository repository, String baseCommitHash, String targetCommitHash) throws IOException {
        try (DiffSession session = new DiffSession(repository)) {
            RevCommit base = session.parseCommit(baseCommitHash);
            RevCommit target = session.parseCommit(targetCommitHash);
            List<FileChange> changedFiles = new ArrayList<>();
            for (DiffEntry entry : session.scan(base, target, TreeFilter.ALL)) {
                Optional<FileChange> changedFile = toFileChange(session, entry);
                if (changedFile.isEmpty()) {
                    continue;
                }
                if (changedFiles.size() == MAX_CHANGED_FILES) {
                    log.info("Metis keeps {} changed files of commit {} in {} and leaves out the others", MAX_CHANGED_FILES, target.name(), repository.getDirectory());
                    break;
                }
                changedFiles.add(changedFile.get());
            }
            changedFiles.sort(Comparator.comparing(FileChange::path));
            boolean setupCommitFound = base.getFullMessage().contains(SET_UP_TEMPLATE_FOR_EXERCISE);
            return new OwnCodeDiff(base.name(), target.name(), setupCommitFound, changedFiles, focusCandidates(changedFiles));
        }
    }

    /**
     * Finds the commit that set up the template in the history of the submitted commit: the first commit whose message contains
     * {@code Set up template for exercise}, where every parent comes before its children whatever dates the commits carry. Walks like
     * {@code AbstractGitService.getFirstCommitWithMessage}, but without falling back to the first commit, and only through the history of the submitted commit:
     * a commit on another branch, or one that claims an earlier date than its parent, cannot become the start of the student's own code. A setup message in an
     * unrelated history that the student merged in is not told apart from the real one, since nothing in the repository records which commit the server created.
     *
     * @param repository   the repository, opened by the caller
     * @param targetCommit the submitted commit, whose history is searched
     * @return the full hash of the setup commit, or null if no commit in the history sets up the template
     * @throws IOException if the history cannot be read or does not contain the submitted commit
     */
    @Nullable
    static String findSetupCommit(Repository repository, ObjectId targetCommit) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            startAtTheRoot(walk, targetCommit);
            for (RevCommit commit = walk.next(); commit != null; commit = walk.next()) {
                if (commit.getFullMessage().contains(SET_UP_TEMPLATE_FOR_EXERCISE)) {
                    return commit.name();
                }
            }
            return null;
        }
    }

    private Repository openRepository(String repositoryUri) {
        LocalVCRepositoryUri uri;
        try {
            uri = new LocalVCRepositoryUri(repositoryUri);
        }
        catch (LocalVCInternalException | IllegalArgumentException e) {
            throw new RepositoryUnavailableException("Invalid repository URI " + repositoryUri, e);
        }
        try {
            return bareGitRepositoryService.getBareRepository(uri, false);
        }
        catch (GitException e) {
            throw new RepositoryUnavailableException("Cannot open the repository " + repositoryUri, e);
        }
    }

    /**
     * The commit the diff starts at: the setup commit in the history of the submitted commit, or the root commit of that history if no commit set up the
     * template, e.g. in a repository created before the setup commits existed.
     */
    private static String findBaseCommit(Repository repository, ObjectId targetCommit) throws IOException {
        String setupCommit = findSetupCommit(repository, targetCommit);
        if (setupCommit != null) {
            return setupCommit;
        }
        String rootCommit = findRootCommit(repository, targetCommit);
        log.debug("No commit in {} sets up the template, so the own code starts at the root commit {} (setupCommitFound = false)", repository.getDirectory(), rootCommit);
        return rootCommit;
    }

    private static String findRootCommit(Repository repository, ObjectId commit) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            startAtTheRoot(walk, commit);
            for (RevCommit candidate = walk.next(); candidate != null; candidate = walk.next()) {
                if (candidate.getParentCount() == 0) {
                    return candidate.name();
                }
            }
        }
        throw new IOException("The history of commit " + commit.name() + " has no root commit");
    }

    /**
     * Lets a walk go through the history of a commit from the oldest commit to the commit itself: every parent before its children, also when the dates of the
     * commits, which the student's client sets, say otherwise; commits of unrelated lines of history in the order of their commit time.
     */
    private static void startAtTheRoot(RevWalk walk, ObjectId commit) throws IOException {
        walk.sort(RevSort.TOPO, true);
        walk.sort(RevSort.COMMIT_TIME_DESC, true);
        walk.sort(RevSort.REVERSE, true);
        walk.markStart(walk.parseCommit(commit));
    }

    /**
     * Reads a focus file at a commit with its change against the base commit, or empty if the file is missing, no regular file, binary or too large.
     */
    private Optional<FocusFileContext> readFocusFile(Repository repository, String baseCommitHash, String commitHash, String path) throws IOException {
        try (DiffSession session = new DiffSession(repository)) {
            RevCommit base = session.parseCommit(baseCommitHash);
            RevCommit target = session.parseCommit(commitHash);
            Optional<byte[]> bytes = session.readTextFile(target, path);
            if (bytes.isEmpty()) {
                return Optional.empty();
            }
            int changedLines = 0;
            String unifiedDiff = null;
            DiffEntry change = session.findChange(base, target, path);
            if (change != null) {
                changedLines = session.countChangedLines(change, bytes.get()).map(LineCounts::total).orElse(0);
                String hunks = session.formatHunks(change);
                unifiedDiff = hunks.isEmpty() ? null : promptDiff(hunks);
            }
            return Optional.of(new FocusFileContext(path, promptContent(bytes.get()), unifiedDiff, changedLines));
        }
    }

    /**
     * Turns one entry of the tree diff into a changed file, or empty if the entry is no own code: a deleted file, a path that is excluded, too long for the
     * focus file column or contains a line break or control character, no regular file, a binary or too large file, or a change that consists of whitespace and
     * blank lines only.
     */
    private Optional<FileChange> toFileChange(DiffSession session, DiffEntry entry) throws IOException {
        if (!isOwnCodePath(entry)) {
            return Optional.empty();
        }
        Optional<byte[]> bytes = session.readTextBlob(entry.getNewId().toObjectId());
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        Optional<LineCounts> counts = session.countChangedLines(entry, bytes.get());
        if (counts.isEmpty() || counts.get().total() == 0) {
            return Optional.empty();
        }
        String content = promptContent(bytes.get());
        String unifiedDiff = promptDiff(session.formatHunks(entry));
        return Optional.of(new FileChange(entry.getNewPath(), counts.get().inserted(), counts.get().deleted(), content, unifiedDiff));
    }

    /**
     * The content of a file as a prompt takes it: null if it is longer than {@code max-focus-file-characters}, so that a prompt shows the diff only.
     */
    @Nullable
    private String promptContent(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        return content.length() <= maxFocusFileCharacters ? content : null;
    }

    /**
     * The hunks of a change as a prompt takes them: cut to {@code max-focus-file-characters}.
     */
    private String promptDiff(String hunks) {
        return MetisPromptTemplateService.truncate(hunks, maxFocusFileCharacters);
    }

    /**
     * Whether the entry is a regular file that still exists and whose path can stand in the focus file column and in a prompt: the prompts show the path outside
     * the markers that fence off student data, so a path that breaks the line could add lines of its own there.
     */
    private static boolean isOwnCodePath(DiffEntry entry) {
        if (entry.getChangeType() == DiffEntry.ChangeType.DELETE || !isRegularFile(entry.getNewMode())) {
            return false;
        }
        String path = entry.getNewPath();
        return path.length() <= MAX_PATH_LENGTH && path.chars().noneMatch(MetisDiffService::isLineBreakOrControlCharacter) && !MetisChangedFileFilter.isExcluded(path);
    }

    /**
     * A control character, including the line feed and the carriage return, or the Unicode line or paragraph separator.
     */
    private static boolean isLineBreakOrControlCharacter(int character) {
        int type = Character.getType(character);
        return Character.isISOControl(character) || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    /**
     * A regular or an executable file; neither a symbolic link nor a submodule, whose object is not even in the repository.
     */
    private static boolean isRegularFile(FileMode mode) {
        return (mode.getBits() & FileMode.TYPE_MASK) == FileMode.TYPE_FILE;
    }

    private List<FileChange> focusCandidates(List<FileChange> changedFiles) {
        List<FileChange> withEnoughChanges = changedFiles.stream().filter(file -> file.changedLines() >= minChangedLines).toList();
        return withEnoughChanges.isEmpty() ? changedFiles : withEnoughChanges;
    }

    private static ObjectId commitId(String commitHash) {
        if (!ObjectId.isId(commitHash)) {
            throw new RepositoryUnavailableException("Not a full commit hash: " + commitHash);
        }
        return ObjectId.fromString(commitHash);
    }

    /**
     * The added and removed lines of a changed file, without blank lines.
     */
    private record LineCounts(int inserted, int deleted) {

        int total() {
            return inserted + deleted;
        }
    }

    /**
     * The JGit resources for the diffs of one repository: a reader with a commit walk, a formatter that scans the trees with rename detection and computes the
     * edits of a file, and a formatter that prints the hunks of one file at a time. Both formatters compare lines ignoring all whitespace and treat files above
     * 512 KiB as binary.
     */
    private static final class DiffSession implements AutoCloseable {

        private final ObjectReader reader;

        private final RevWalk walk;

        private final DiffFormatter counter;

        private final ByteArrayOutputStream hunkBuffer = new ByteArrayOutputStream();

        private final DiffFormatter printer;

        private DiffSession(Repository repository) {
            reader = repository.newObjectReader();
            walk = new RevWalk(reader);
            counter = formatter(repository, DisabledOutputStream.INSTANCE);
            counter.setDetectRenames(true);
            printer = formatter(repository, hunkBuffer);
        }

        private static DiffFormatter formatter(Repository repository, OutputStream output) {
            DiffFormatter formatter = new DiffFormatter(output);
            formatter.setRepository(repository);
            formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            formatter.setBinaryFileThreshold(MAX_FILE_BYTES);
            formatter.setContext(DIFF_CONTEXT_LINES);
            return formatter;
        }

        private RevCommit parseCommit(String commitHash) throws IOException {
            return walk.parseCommit(commitId(commitHash));
        }

        private List<DiffEntry> scan(RevCommit base, RevCommit target, TreeFilter pathFilter) throws IOException {
            counter.setPathFilter(pathFilter);
            return counter.scan(treeParser(base), treeParser(target));
        }

        private CanonicalTreeParser treeParser(RevCommit commit) throws IOException {
            CanonicalTreeParser parser = new CanonicalTreeParser();
            parser.reset(reader, commit.getTree());
            return parser;
        }

        /**
         * The change of one file between the commits, or null if the file did not change. A file that looks added is looked up again among all changes, where
         * rename detection can find the template file it was renamed or copied from, as in the diff of the generation; that scan contains the file as added,
         * renamed or copied.
         */
        @Nullable
        private DiffEntry findChange(RevCommit base, RevCommit target, String path) throws IOException {
            DiffEntry change = changeOf(scan(base, target, PathFilter.create(path)), path);
            if (change == null || change.getChangeType() != DiffEntry.ChangeType.ADD) {
                return change;
            }
            return changeOf(scan(base, target, TreeFilter.ALL), path);
        }

        @Nullable
        private static DiffEntry changeOf(List<DiffEntry> entries, String path) {
            return entries.stream().filter(entry -> path.equals(entry.getNewPath())).findFirst().orElse(null);
        }

        /**
         * The content of a regular text file at a commit, or empty if the path is missing, no regular file, binary or larger than 512 KiB.
         */
        private Optional<byte[]> readTextFile(RevCommit commit, String path) throws IOException {
            try (TreeWalk file = TreeWalk.forPath(reader, path, commit.getTree())) {
                if (file == null || !isRegularFile(file.getFileMode(0))) {
                    return Optional.empty();
                }
                return readTextBlob(file.getObjectId(0));
            }
        }

        /**
         * The content of a blob, or empty if it is larger than 512 KiB or binary by its content (a script counts as text whatever its name).
         */
        private Optional<byte[]> readTextBlob(ObjectId blobId) throws IOException {
            ObjectLoader loader = reader.open(blobId, Constants.OBJ_BLOB);
            if (loader.getSize() > MAX_FILE_BYTES) {
                return Optional.empty();
            }
            byte[] bytes = loader.getCachedBytes(MAX_FILE_BYTES);
            return RawText.isBinary(bytes) ? Optional.empty() : Optional.of(bytes);
        }

        /**
         * Counts the added and removed lines of a change from the edits of the formatter, leaving out blank lines: comparing lines ignoring all whitespace
         * already hides re-indented lines, but an added or removed blank line still forms an edit of its own.
         *
         * @return the counts, or empty if the formatter cannot compare the file as text (the template side is binary or too large)
         */
        private Optional<LineCounts> countChangedLines(DiffEntry change, byte[] newContent) throws IOException {
            FileHeader header = counter.toFileHeader(change);
            if (header.getPatchType() != FileHeader.PatchType.UNIFIED) {
                return Optional.empty();
            }
            RawText oldText = change.getChangeType() == DiffEntry.ChangeType.ADD ? RawText.EMPTY_TEXT : new RawText(readBlob(change.getOldId().toObjectId()));
            RawText newText = new RawText(newContent);
            int inserted = 0;
            int deleted = 0;
            for (Edit edit : header.toEditList()) {
                deleted += countNonBlankLines(oldText, edit.getBeginA(), edit.getEndA());
                inserted += countNonBlankLines(newText, edit.getBeginB(), edit.getEndB());
            }
            return Optional.of(new LineCounts(inserted, deleted));
        }

        private byte[] readBlob(ObjectId blobId) throws IOException {
            return reader.open(blobId, Constants.OBJ_BLOB).getCachedBytes(MAX_FILE_BYTES);
        }

        private static int countNonBlankLines(RawText text, int begin, int end) {
            int count = 0;
            for (int line = begin; line < end; line++) {
                if (!text.getString(line).isBlank()) {
                    count++;
                }
            }
            return count;
        }

        /**
         * The hunks of one change with three lines of context, without the file header ({@code diff --git}, {@code index}, the {@code ---} and {@code +++}
         * lines), whose lines would look like removed and added code next to the hunks.
         *
         * @return the hunks, empty if the change has none
         */
        private String formatHunks(DiffEntry change) throws IOException {
            hunkBuffer.reset();
            printer.format(change);
            printer.flush();
            String formatted = hunkBuffer.toString(StandardCharsets.UTF_8);
            int lineBeforeFirstHunk = formatted.indexOf("\n" + HUNK_START);
            return lineBeforeFirstHunk < 0 ? "" : formatted.substring(lineBeforeFirstHunk + 1);
        }

        @Override
        public void close() {
            printer.close();
            counter.close();
            walk.close();
            reader.close();
        }
    }
}
