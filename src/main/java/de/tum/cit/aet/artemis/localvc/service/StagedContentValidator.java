package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.localvc.service.git.GitPathNames;

/**
 * Checks the staged content of a working copy against the same limits that the pre-receive hook applies to a push, before a commit of the online editor is created. A commit of
 * the online editor is pushed directly into the repository of the server, so the checks of the hook are not run for it.
 * <p>
 * The files that are new or changed compared to the accepted destination branch are checked one by one, and the whole tree against the limits for its size, so that content that is
 * part of the
 * repository already (for example from a template) does not prevent a commit that does not make the repository larger.
 */
public final class StagedContentValidator {

    private static final Logger log = LoggerFactory.getLogger(StagedContentValidator.class);

    /** The prefix of the error keys when the staged content cannot be committed. */
    public static final String ERROR_KEY = "stagedContentNotAllowed";

    /** The longest path of a file that is accepted. */
    static final int MAX_PATH_LENGTH = 4096;

    private static final String ENTITY_NAME = "Repository";

    private StagedContentValidator() {
        // utility class
    }

    /**
     * Validates the staged content of a repository like the pre-receive hook validates a push.
     * <ul>
     * <li>The files that are new or changed compared to the accepted destination branch are checked for the depth of the directories, the length of the paths, the size of the
     * file, the names of
     * the entries, and that they are no symbolic links or submodules.</li>
     * <li>The whole staged tree is checked for the number of entries (files and directories) and the size of all files together. A staged tree above a limit is refused only if it
     * is larger than the accepted destination tree, so that a commit that does not make an oversized repository larger is accepted. Unpushed local commits are not a baseline.</li>
     * </ul>
     *
     * @param repository             the working copy whose index is examined
     * @param destination            the repository receiving the push, whose matching branch supplies the accepted baseline
     * @param maxRepositorySizeBytes the size that the files of the repository may add up to, as for a push to that repository
     * @throws BadRequestAlertException if the staged content violates one of the limits, or cannot be read
     */
    public static void validate(Repository repository, Repository destination, long maxRepositorySizeBytes) {
        validate(repository, destination, maxRepositorySizeBytes, LocalVCPrePushHook.MAX_TREE_ENTRIES);
    }

    static void validate(Repository repository, Repository destination, long maxRepositorySizeBytes, int maxEntries) {
        try {
            validateStagedTree(repository, destination, Math.min(maxRepositorySizeBytes, LocalVCPrePushHook.MAX_TREE_BYTES), maxEntries);
        }
        catch (IOException e) {
            log.warn("The staged content of {} could not be examined: {}", repository.getDirectory(), e.getMessage());
            reject("The staged content of the repository could not be examined", "unreadable", Map.of());
        }
    }

    private static void validateStagedTree(Repository repository, Repository destination, long maxBytes, int maxEntries) throws IOException {
        DirCache dirCache = repository.readDirCache();
        String branch = repository.getFullBranch();
        if (branch == null || !branch.startsWith(Constants.R_HEADS)) {
            throw new IOException("The working copy is not on a branch");
        }
        ObjectId acceptedTree = destination.resolve(branch + "^{tree}");
        try (ObjectReader reader = repository.newObjectReader();
                ObjectReader destinationReader = destination.newObjectReader();
                TreeWalk treeWalk = new TreeWalk(repository, reader)) {
            // The destination can have accepted objects that the working copy has not fetched. Materialize its index
            // with its own reader so recursive comparison never tries to load those objects from the working copy.
            AbstractTreeIterator accepted = acceptedTree == null ? new EmptyTreeIterator() : new DirCacheIterator(DirCache.read(destinationReader, acceptedTree));
            treeWalk.addTree(accepted);
            treeWalk.addTree(new DirCacheIterator(dirCache));
            treeWalk.setRecursive(true);
            long stagedBytes = 0;
            long stagedFiles = 0;
            Set<String> stagedDirectories = new HashSet<>();
            while (treeWalk.next()) {
                FileMode newMode = treeWalk.getFileMode(1);
                if (FileMode.MISSING.equals(newMode)) {
                    // removed
                    continue;
                }
                stagedFiles++;
                addParentDirectories(stagedDirectories, treeWalk.getPathString());
                boolean unchanged = newMode.equals(treeWalk.getFileMode(0)) && treeWalk.idEqual(0, 1);
                if (!unchanged) {
                    validateChangedEntry(treeWalk, newMode);
                }
                if (!FileMode.GITLINK.equals(newMode)) {
                    // only the size is read, not the content
                    stagedBytes += reader.getObjectSize(treeWalk.getObjectId(1), Constants.OBJ_BLOB);
                }
            }
            long stagedEntries = stagedFiles + stagedDirectories.size();
            if (stagedEntries > maxEntries && stagedEntries > countEntries(destinationReader, acceptedTree)) {
                reject("The repository contains too many files and directories", "tooManyEntries", Map.of("limit", maxEntries));
            }
            if (stagedBytes > maxBytes && stagedBytes > sizeOfFiles(destinationReader, acceptedTree)) {
                reject("The repository would be %.1f MB large, but the limit is %d MB. Remove large files and commit again.".formatted(stagedBytes / (1024.0 * 1024.0),
                        maxBytes / (1024 * 1024)), "repositoryTooLarge", Map.of("size", stagedBytes, "limit", maxBytes));
            }
        }
    }

    private static void validateChangedEntry(TreeWalk treeWalk, FileMode newMode) throws IOException {
        String path = treeWalk.getPathString();
        if (path.length() > MAX_PATH_LENGTH) {
            reject("The path of a file is too long: " + abbreviate(path), "pathTooLong", Map.of("path", abbreviate(path), "limit", MAX_PATH_LENGTH));
        }
        if (treeWalk.getDepth() > LocalVCPrePushHook.MAX_TREE_DEPTH) {
            reject("The path of a file is too deeply nested: " + abbreviate(path), "pathTooDeep", Map.of("path", abbreviate(path), "limit", LocalVCPrePushHook.MAX_TREE_DEPTH));
        }
        if (GitPathNames.containsGitMetadataName(path)) {
            reject("The path is not allowed: " + abbreviate(path), "pathNotAllowed", Map.of("path", abbreviate(path)));
        }
        if (FileMode.SYMLINK.equals(newMode) || FileMode.GITLINK.equals(newMode)) {
            reject("Symbolic links and submodules are not allowed: " + abbreviate(path), "linksNotAllowed", Map.of("path", abbreviate(path)));
        }
        long size = treeWalk.getObjectReader().getObjectSize(treeWalk.getObjectId(1), Constants.OBJ_BLOB);
        if (size > LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES) {
            reject("The file exceeds the size limit of " + LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + " bytes: " + abbreviate(path), "fileTooLarge",
                    Map.of("path", abbreviate(path), "limit", LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES));
        }
    }

    /** Adds the directories above a file that are not known yet. A known directory has all its parents known as well. */
    private static void addParentDirectories(Set<String> directories, String path) {
        int slash = path.lastIndexOf('/');
        while (slash > 0) {
            if (!directories.add(path.substring(0, slash))) {
                return;
            }
            slash = path.lastIndexOf('/', slash - 1);
        }
    }

    /** The number of entries, files and directories, of a tree, counted like the hook counts them. */
    private static long countEntries(ObjectReader reader, ObjectId tree) throws IOException {
        if (tree == null) {
            return 0;
        }
        long entries = 0;
        try (TreeWalk treeWalk = new TreeWalk(reader)) {
            treeWalk.addTree(tree);
            while (treeWalk.next()) {
                entries++;
                if (treeWalk.isSubtree()) {
                    treeWalk.enterSubtree();
                }
            }
        }
        return entries;
    }

    /** The size of all files of a tree. */
    private static long sizeOfFiles(ObjectReader reader, ObjectId tree) throws IOException {
        if (tree == null) {
            return 0;
        }
        long size = 0;
        try (TreeWalk treeWalk = new TreeWalk(reader)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                if (!FileMode.GITLINK.equals(treeWalk.getFileMode(0))) {
                    size += reader.getObjectSize(treeWalk.getObjectId(0), Constants.OBJ_BLOB);
                }
            }
        }
        return size;
    }

    private static void reject(String message, String reason, Map<String, Object> parameters) {
        log.warn("Rejected the staged content of an online editor commit: {}", message);
        String errorKey = ERROR_KEY + "." + reason;
        throw new BadRequestAlertException(ErrorConstants.PARAMETERIZED_TYPE, message, ENTITY_NAME, errorKey, Map.of("message", "error." + errorKey, "params", parameters));
    }

    private static String abbreviate(String path) {
        return path.length() > 200 ? path.substring(0, 200) + "..." : path;
    }
}
