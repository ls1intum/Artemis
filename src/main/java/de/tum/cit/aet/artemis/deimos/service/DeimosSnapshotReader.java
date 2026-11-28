package de.tum.cit.aet.artemis.deimos.service;

import static de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration.isBinaryFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration;
import de.tum.cit.aet.artemis.deimos.config.DeimosEnabled;

/**
 * Reads the files of one commit as evidence for the Deimos analysis.
 * <p>
 * The repository exports drop files by extension alone, and {@link BinaryFileExtensionConfiguration} counts shell and
 * batch scripts as binary, although they are exactly where an attack on the build infrastructure would be written. This
 * reader therefore leaves a file out only when both its extension and the beginning of its content mark it as binary,
 * and never leaves out a script. All entries it does not return as text are reported with their object id, so that
 * their paths can still be checked and a change to their content can be recognised without loading them.
 */
@Conditional(DeimosEnabled.class)
@Lazy
@Service
public class DeimosSnapshotReader {

    /**
     * Script types that {@link BinaryFileExtensionConfiguration} counts as binary although they are text. A shell still
     * executes such a script when it contains a NUL byte, so its content must not be classified away.
     */
    private static final List<String> TEXT_SCRIPT_EXTENSIONS = List.of(".sh", ".bat");

    /**
     * The largest text file that is read into memory, the same bound as for reading all files of a repository elsewhere.
     */
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /**
     * The most that the text files of one commit add up to.
     */
    static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;

    /**
     * The most entries of one commit that are read.
     */
    static final int MAX_ENTRIES = 100_000;

    /**
     * The files of one commit, split into text files and everything else.
     *
     * @param textFiles  the text files, repository-relative path to UTF-8 content
     * @param otherFiles all other entries (binary files, symbolic links and submodules), repository-relative path to the
     *                       id of the object the entry points to
     */
    public record TextSnapshot(Map<String, String> textFiles, Map<String, ObjectId> otherFiles) {
    }

    /**
     * Reads every entry of a commit.
     * <p>
     * A file without a binary extension is always returned as text. For a file with such an extension only the
     * beginning is read to classify it, so an archive or image is recognised without loading it; a file whose beginning
     * is text is loaded completely, like every other text file.
     * <p>
     * A commit beyond {@link #MAX_FILE_BYTES}, {@link #MAX_TOTAL_BYTES} or {@link #MAX_ENTRIES} is an error rather than a
     * smaller result. The whole snapshot is held in memory before any payload limit applies, and leaving the excess out
     * instead would hide its content from the marker scan, which a student could use to evade it.
     *
     * @param repository the repository
     * @param commitHash the commit to read
     * @return the text files and the remaining entries of that commit, both empty if the commit cannot be resolved
     * @throws IOException if the repository cannot be read or the commit exceeds a limit
     */
    public TextSnapshot read(Repository repository, String commitHash) throws IOException {
        return read(repository, commitHash, MAX_FILE_BYTES, MAX_TOTAL_BYTES, MAX_ENTRIES);
    }

    static TextSnapshot read(Repository repository, String commitHash, long maxFileBytes, long maxTotalBytes, int maxEntries) throws IOException {
        ObjectId commitId = repository.resolve(commitHash + "^{commit}");
        if (commitId == null) {
            return new TextSnapshot(Map.of(), Map.of());
        }

        Map<String, String> textFiles = new HashMap<>();
        Map<String, ObjectId> otherFiles = new HashMap<>();
        long totalBytes = 0;
        try (RevWalk revWalk = new RevWalk(repository); TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(revWalk.parseCommit(commitId).getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(new EntryLimit(maxEntries));
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                FileMode fileMode = treeWalk.getFileMode(0);
                ObjectId objectId = treeWalk.getObjectId(0);
                if (fileMode == FileMode.SYMLINK || fileMode.getObjectType() != Constants.OBJ_BLOB) {
                    otherFiles.put(path, objectId);
                }
                else {
                    ObjectLoader blobLoader = repository.open(objectId);
                    if (isBinaryFile(path) && !isTextScript(path) && startsWithBinaryContent(blobLoader)) {
                        otherFiles.put(path, objectId);
                    }
                    else {
                        textFiles.put(path, readText(blobLoader, maxFileBytes, maxTotalBytes - totalBytes, maxTotalBytes));
                        totalBytes += blobLoader.getSize();
                    }
                }
            }
        }
        return new TextSnapshot(textFiles, otherFiles);
    }

    /**
     * Loads a text file, failing rather than holding a file or a commit beyond the limits in memory. The path is the
     * student's, so it stays out of the message, which may reach a report.
     */
    private static String readText(ObjectLoader blobLoader, long maxFileBytes, long remainingBytes, long maxTotalBytes) throws IOException {
        long size = blobLoader.getSize();
        if (size > maxFileBytes) {
            throw new IOException("A file of the commit is larger than " + maxFileBytes + " bytes");
        }
        if (size > remainingBytes) {
            throw new IOException("The text files of the commit add up to more than " + maxTotalBytes + " bytes");
        }
        try (InputStream inputStream = blobLoader.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Counts every entry the walk looks at, directories included, before it descends into them. Counting only the files
     * the walk returns would leave a tree that references the same or empty subtrees many times unbounded.
     */
    static final class EntryLimit extends TreeFilter {

        private final int maxEntries;

        private int visitedEntries;

        EntryLimit(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        @Override
        public boolean include(TreeWalk walker) throws IOException {
            visitedEntries++;
            if (visitedEntries > maxEntries) {
                throw new IOException("The commit has more than " + maxEntries + " entries");
            }
            return true;
        }

        @Override
        public boolean shouldBeRecursive() {
            return false;
        }

        // NOSONAR below: TreeFilter declares clone() abstract without being Cloneable, so neither removing it nor super.clone() is possible.
        @Override
        public TreeFilter clone() { // NOSONAR
            return new EntryLimit(maxEntries);
        }
    }

    private static boolean isTextScript(String path) {
        String lowerCasePath = path.toLowerCase(Locale.ROOT);
        return TEXT_SCRIPT_EXTENSIONS.stream().anyMatch(lowerCasePath::endsWith);
    }

    /**
     * Classifies a blob as binary from its beginning alone, without loading the rest of it.
     *
     * @param blobLoader the loader of the blob
     * @return whether the beginning of the blob is binary
     * @throws IOException if the blob cannot be read
     */
    private static boolean startsWithBinaryContent(ObjectLoader blobLoader) throws IOException {
        try (InputStream inputStream = blobLoader.openStream()) {
            return RawText.isBinary(inputStream);
        }
    }
}
