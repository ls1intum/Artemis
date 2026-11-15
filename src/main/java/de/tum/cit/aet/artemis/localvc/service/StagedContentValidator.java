package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.localvc.service.git.GitPathNames;

/**
 * Checks the staged content of a working copy against the same limits that the pre-receive hook applies to a push, before a commit of the online editor is created. A commit of
 * the online editor is pushed directly into the repository of the server, so the checks of the hook are not run for it.
 * <p>
 * Only the files that are new or changed compared to the last commit are checked, so that content that is part of the repository already (for example from a template) does not
 * prevent a commit that leaves it as it is.
 */
public final class StagedContentValidator {

    private static final Logger log = LoggerFactory.getLogger(StagedContentValidator.class);

    /** The error key of the response when the staged content violates a limit. */
    public static final String ERROR_KEY = "stagedContentNotAllowed";

    /** The longest path of a file that is accepted. */
    static final int MAX_PATH_LENGTH = 4096;

    private static final String ENTITY_NAME = "Repository";

    private StagedContentValidator() {
        // utility class
    }

    /**
     * Validates the files that are staged and differ from the last commit: the depth of the directories, the length of the paths, the size of every file and of all of them
     * together, the names of the entries, and that no entry is a symbolic link or a submodule. The number of files is checked if the commit adds files.
     *
     * @param repository the working copy whose index is examined
     * @throws BadRequestAlertException if the staged content violates one of the limits, or cannot be read
     */
    public static void validate(Repository repository) {
        try {
            validateChangedEntries(repository);
        }
        catch (IOException e) {
            log.warn("The staged content of {} could not be examined: {}", repository.getDirectory(), e.getMessage());
            throw new BadRequestAlertException("The staged content of the repository could not be examined", ENTITY_NAME, ERROR_KEY);
        }
    }

    private static void validateChangedEntries(Repository repository) throws IOException {
        DirCache dirCache = repository.readDirCache();
        ObjectId headTree = repository.resolve(Constants.HEAD + "^{tree}");
        try (ObjectReader reader = repository.newObjectReader(); TreeWalk treeWalk = new TreeWalk(repository, reader)) {
            AbstractTreeIterator head = headTree == null ? new EmptyTreeIterator() : new CanonicalTreeParser(null, reader, headTree);
            treeWalk.addTree(head);
            treeWalk.addTree(new DirCacheIterator(dirCache));
            treeWalk.setRecursive(true);
            long addedBytes = 0;
            long addedEntries = 0;
            while (treeWalk.next()) {
                FileMode newMode = treeWalk.getFileMode(1);
                if (FileMode.MISSING.equals(newMode)) {
                    // removed
                    continue;
                }
                if (newMode.equals(treeWalk.getFileMode(0)) && treeWalk.idEqual(0, 1)) {
                    // the same as in the last commit
                    continue;
                }
                String path = treeWalk.getPathString();
                if (FileMode.MISSING.equals(treeWalk.getFileMode(0))) {
                    addedEntries++;
                }
                if (path.length() > MAX_PATH_LENGTH || treeWalk.getDepth() > LocalVCPrePushHook.MAX_TREE_DEPTH) {
                    reject("The path of a file is too long or too deeply nested: " + abbreviate(path));
                }
                if (GitPathNames.containsGitMetadataName(path)) {
                    reject("The path is not allowed: " + abbreviate(path));
                }
                if (FileMode.SYMLINK.equals(newMode) || FileMode.GITLINK.equals(newMode)) {
                    reject("Symbolic links and submodules are not allowed: " + abbreviate(path));
                }
                long size = reader.getObjectSize(treeWalk.getObjectId(1), Constants.OBJ_BLOB);
                if (size > LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES) {
                    reject("The file exceeds the size limit of " + LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + " bytes: " + abbreviate(path));
                }
                addedBytes += size;
                if (addedBytes > LocalVCPrePushHook.MAX_TREE_BYTES) {
                    reject("The changed files exceed the total size limit");
                }
            }
            if (addedEntries > 0 && dirCache.getEntryCount() > LocalVCPrePushHook.MAX_TREE_ENTRIES) {
                reject("The repository contains too many files");
            }
        }
    }

    private static void reject(String message) {
        log.warn("Rejected the staged content of an online editor commit: {}", message);
        throw new BadRequestAlertException(message, ENTITY_NAME, ERROR_KEY);
    }

    private static String abbreviate(String path) {
        return path.length() > 200 ? path.substring(0, 200) + "..." : path;
    }
}
