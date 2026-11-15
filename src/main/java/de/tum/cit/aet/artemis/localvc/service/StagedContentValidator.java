package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

import de.tum.cit.aet.artemis.localvc.service.git.GitPathNames;

/**
 * Checks the staged content of a working copy against the same limits that the pre-receive hook applies to a push, before a commit of the online editor is created. A commit of
 * the online editor is pushed directly into the repository of the server, so the checks of the hook are not run for it.
 */
public final class StagedContentValidator {

    /** The longest path of a file that is accepted. */
    static final int MAX_PATH_LENGTH = 4096;

    private StagedContentValidator() {
        // utility class
    }

    /**
     * Validates the staged files of a repository: the number of files, the depth of the directories, the length of the paths, the size of every file and of all files together,
     * the names of the entries, and that no entry is a symbolic link or a submodule.
     *
     * @param repository the working copy whose index is examined
     * @throws IllegalArgumentException if the staged content violates one of the limits, or cannot be read
     */
    public static void validate(Repository repository) {
        try {
            validateIndex(repository);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("The staged content of the repository could not be examined", e);
        }
    }

    private static void validateIndex(Repository repository) throws IOException {
        DirCache dirCache = repository.readDirCache();
        if (dirCache.getEntryCount() > LocalVCPrePushHook.MAX_TREE_ENTRIES) {
            throw new IllegalArgumentException("The repository contains too many files");
        }
        long totalBytes = 0;
        try (ObjectReader reader = repository.newObjectReader()) {
            for (int i = 0; i < dirCache.getEntryCount(); i++) {
                DirCacheEntry entry = dirCache.getEntry(i);
                String path = entry.getPathString();
                if (path.length() > MAX_PATH_LENGTH || depthOf(path) > LocalVCPrePushHook.MAX_TREE_DEPTH) {
                    throw new IllegalArgumentException("The path of a file is too long or too deeply nested: " + abbreviate(path));
                }
                if (GitPathNames.containsGitMetadataName(path)) {
                    throw new IllegalArgumentException("The path is not allowed: " + abbreviate(path));
                }
                FileMode mode = entry.getFileMode();
                if (FileMode.SYMLINK.equals(mode) || FileMode.GITLINK.equals(mode)) {
                    throw new IllegalArgumentException("Symbolic links and submodules are not allowed: " + abbreviate(path));
                }
                long size = reader.getObjectSize(entry.getObjectId(), Constants.OBJ_BLOB);
                if (size > LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES) {
                    throw new IllegalArgumentException("The file exceeds the size limit of " + LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + " bytes: " + abbreviate(path));
                }
                totalBytes += size;
                if (totalBytes > LocalVCPrePushHook.MAX_TREE_BYTES) {
                    throw new IllegalArgumentException("The files of the repository exceed the total size limit");
                }
            }
        }
    }

    /** The number of directories above the file of the path. */
    private static int depthOf(String path) {
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    private static String abbreviate(String path) {
        return path.length() > 200 ? path.substring(0, 200) + "..." : path;
    }
}
