package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration.isBinaryFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads file content of a commit through one shared JGit traversal for full-repository exports, selected files, single-file lookups and the bounded context of a language
 * model.
 */
final class RepositoryContentReader {

    private static final Logger log = LoggerFactory.getLogger(RepositoryContentReader.class);

    /** The most entries of a tree that are visited when all files of a repository are read. */
    static final int MAX_TREE_ENTRIES = 100_000;

    enum BinaryFileFilter {
        NONE, EXTENSION, CONTENT
    }

    private RepositoryContentReader() {
    }

    /**
     * Reads file content of a commit.
     *
     * @param repository          The repository from which file contents are retrieved.
     * @param commitId            The commit from which file contents are retrieved.
     * @param selectedFilePaths   The paths to retrieve, or null to traverse all files.
     * @param binaryFileFilter    The strategy used to exclude binary files.
     * @param maxFileSizeBytes    The maximum size of an individual returned file.
     * @param maxTotalSizeBytes   The maximum aggregate size of all returned files.
     * @param failOnLimit         Whether a total size above its maximum is an error. Otherwise the files that no longer fit are left out.
     * @param failOnOversizedFile Whether a file above its maximum is an error. Otherwise the file is left out.
     * @return A map of repository-relative file paths to UTF-8 content.
     * @throws IOException If the commit or file content cannot be read, if the tree has more entries than {@link #MAX_TREE_ENTRIES}, or if a size limit is exceeded and
     *                         {@code failOnLimit} or {@code failOnOversizedFile} is set.
     */
    static Map<String, String> read(Repository repository, @NonNull ObjectId commitId, @Nullable Set<String> selectedFilePaths, BinaryFileFilter binaryFileFilter,
            long maxFileSizeBytes, long maxTotalSizeBytes, boolean failOnLimit, boolean failOnOversizedFile) throws IOException {
        return read(repository, commitId, selectedFilePaths, binaryFileFilter, maxFileSizeBytes, maxTotalSizeBytes, failOnLimit, failOnOversizedFile, null, null);
    }

    /**
     * Like the method above, with a path filter (applied before sizes are counted) and an optional collector for paths skipped because of their size.
     *
     * @param pathFilter   accepts the paths to read, or null to read all
     * @param omittedPaths receives the paths that are left out because of their size, or null
     * @return A map of repository-relative file paths to UTF-8 content.
     * @throws IOException see above
     */
    static Map<String, String> read(Repository repository, @NonNull ObjectId commitId, @Nullable Set<String> selectedFilePaths, BinaryFileFilter binaryFileFilter,
            long maxFileSizeBytes, long maxTotalSizeBytes, boolean failOnLimit, boolean failOnOversizedFile, @Nullable Predicate<String> pathFilter,
            @Nullable Set<String> omittedPaths) throws IOException {
        Map<String, String> filesWithContent = new HashMap<>();
        long totalSize = 0;

        try (RevWalk revWalk = new RevWalk(repository); TreeWalk treeWalk = new TreeWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            if (selectedFilePaths != null && selectedFilePaths.isEmpty()) {
                return Map.of();
            }
            // Every entry the walk looks at is counted, also the ones that a path filter rejects. A tree that is bigger than the limit is an error, not a smaller result.
            treeWalk.setFilter(new EntryLimitTreeFilter(selectedFilePaths != null ? PathFilterGroup.createFromStrings(selectedFilePaths) : TreeFilter.ALL, MAX_TREE_ENTRIES));

            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (selectedFilePaths != null && !selectedFilePaths.contains(path)) {
                    continue;
                }
                if (treeWalk.getFileMode(0) == FileMode.SYMLINK || treeWalk.getFileMode(0).getObjectType() != Constants.OBJ_BLOB
                        || binaryFileFilter == BinaryFileFilter.EXTENSION && isBinaryFile(path) || pathFilter != null && !pathFilter.test(path)) {
                    continue;
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                try {
                    var blobLoader = repository.open(objectId);
                    long blobSize = blobLoader.getSize();
                    if (blobSize > maxFileSizeBytes) {
                        if (failOnOversizedFile) {
                            throw new IOException("The file " + path + " is larger than " + maxFileSizeBytes + " bytes.");
                        }
                        // The selected files of a read with limits of its own are left out, like a binary file
                        if (omittedPaths != null) {
                            omittedPaths.add(path);
                        }
                        log.debug("Leaving out the file {} of the commit {}: it is larger than {} bytes", path, commitId.name(), maxFileSizeBytes);
                        continue;
                    }
                    if (blobSize > maxTotalSizeBytes - totalSize) {
                        if (failOnLimit) {
                            throw new IOException("The files of the commit add up to more than " + maxTotalSizeBytes + " bytes.");
                        }
                        if (omittedPaths != null) {
                            omittedPaths.add(path);
                        }
                        // Files that fit stay available. Failing would hide all files of a repository that only has a few large ones, e.g. in a dependency folder.
                        log.debug("Leaving out the file {} of the commit {}: the files read so far use up the budget of {} bytes", path, commitId.name(), maxTotalSizeBytes);
                        continue;
                    }
                    byte[] bytes;
                    try (InputStream inputStream = blobLoader.openStream()) {
                        bytes = inputStream.readAllBytes();
                    }
                    if (binaryFileFilter == BinaryFileFilter.CONTENT && RawText.isBinary(bytes)) {
                        continue;
                    }
                    filesWithContent.put(path, new String(bytes, StandardCharsets.UTF_8));
                    totalSize += blobSize;
                }
                catch (MissingObjectException e) {
                    // Log diagnostic info to help debug intermittent CI failures where objects
                    // appear missing despite being flushed to disk moments before
                    var repoDir = repository.getDirectory();
                    var objectsDir = repoDir != null ? repoDir.toPath().resolve("objects") : null;
                    log.error(
                            "MissingObjectException reading blob {} for file '{}' at commit {} in repo {}. " + "Objects dir exists: {}, pack dir exists: {}, repo isBare: {}. "
                                    + "This may indicate a JGit object visibility race condition on the CI filesystem.",
                            objectId.name(), path, commitId.name(), repoDir, objectsDir != null && Files.exists(objectsDir),
                            objectsDir != null && Files.exists(objectsDir.resolve("pack")), repository.isBare());
                    throw e;
                }
            }
        }
        return filesWithContent;
    }
}
