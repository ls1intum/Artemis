package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;

/**
 * Contains an onPreReceive method that is called by JGit before a push is received (i.e. before the pushed files are written to disk but after the authorization check was
 * successful).
 */
public record LocalVCPrePushHook(LocalVCServletService localVCServletService, User user) implements PreReceiveHook {

    private static final Logger log = LoggerFactory.getLogger(LocalVCPrePushHook.class);

    public LocalVCPrePushHook(LocalVCServletService localVCServletService, @NonNull User user) {
        this.localVCServletService = localVCServletService;
        this.user = user;
    }

    private static final long MAX_BLOB_SIZE_BYTES = 10L * 1024 * 1024; // 10MB

    private static final String REPOSITORY_SIZE_DOCUMENTATION_URL = "https://docs.artemis.tum.de/instructor/exercises/programming-exercise/write-code-and-tests#keep-repositories-small";

    /**
     * Called by JGit before a push is received (i.e. before the pushed files are written to disk but after the authorization check was successful).
     *
     * @param receivePack the process handling the current receive. Hooks may obtain details about the destination repository through this handle.
     * @param commands    ReceiveCommands that each correspond to a single ref update (e.g. update to a branch or a tag).
     */
    @Override
    public void onPreReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {

        if (commands.isEmpty()) {
            // E.g. no refs were updated during the push operation.
            // Note: There is no command that we can set a REJECTED Result on so this will continue into the LocalVCPostPushHook and we have to return there again.
            return;
        }

        // There should only be one ReceiveCommand. If there are multiple, e.g. because the user pushes to multiple branches simultaneously, we reject the push.
        // Every single command has to be rejected here: JGit applies every command that a hook leaves on NOT_ATTEMPTED, so rejecting only one of them would let
        // the remaining refs through without any of the checks below.
        if (commands.size() > 1) {
            commands.forEach(multiRefCommand -> multiRefCommand.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot push multiple refs at once."));
            return;
        }

        ReceiveCommand command = commands.iterator().next();

        Repository repository = receivePack.getRepository();

        String defaultBranchName;
        try {
            defaultBranchName = repository.getBranch();
            if (defaultBranchName == null) {
                throw new LocalVCInternalException("HEAD is not a symbolic reference in repository " + repository.getDirectory().toPath());
            }
        }
        catch (LocalVCInternalException | IOException e) {
            log.warn("An error occurred while checking the default branch: {}", e.getMessage());
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "An error occurred while checking the branch.");
            return;
        }

        // If branching is allowed, reject pushes to anything other than branch names that match the branch regex
        // If branching is not allowed, reject pushes to anything other than the default branch
        String branchPath = command.getRefName();
        if (!branchPath.equals("refs/heads/" + defaultBranchName)) {
            String branchName = branchPath.substring("refs/heads/".length());
            LocalVCServletService.BranchingStatus branchingStatus = localVCServletService.isBranchNameAllowedForRepository(repository, branchName);
            switch (branchingStatus) {
                case BRANCHING_DISABLED -> {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot push to a branch other than the default branch.");
                    return;
                }
                case NAME_DOES_NOT_MATCH_REGEX -> {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON,
                            "You cannot push to a branch other than the default branch that does not match the regex for branch names in this repository.");
                    return;
                }
                default -> {
                }
            }
        }

        try (Git git = new Git(repository)) {
            // Prevent deletion of branches.
            Ref ref = git.getRepository().exactRef(command.getRefName());
            if (ref != null && command.getNewId().equals(ObjectId.zeroId())) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot delete a branch.");
                return;
            }

            // Prevent force push.
            if (command.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
                try {
                    if (!localVCServletService.isUserAllowedToForcePush(user, repository)) {
                        command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot force push.");
                        return;
                    }
                }
                catch (Exception e) {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "An error occurred while checking the user's permissions.");
                    return;
                }
            }

            rejectFilesLargerThan10MbOrSymlinksOrSubModulesOrOversizedRepository(repository, command, localVCServletService.getMaxRepositorySizeBytes(repository));
        }
        catch (IOException e) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "An error occurred while checking the branch.");
        }
    }

    private static void rejectFilesLargerThan10MbOrSymlinksOrSubModulesOrOversizedRepository(Repository repository, ReceiveCommand command, long maxRepositorySizeBytes)
            throws IOException {
        try (ObjectReader reader = repository.newObjectReader(); RevWalk revWalk = new RevWalk(reader); TreeWalk treeWalk = new TreeWalk(reader)) {
            RevCommit newCommit = revWalk.parseCommit(command.getNewId());
            treeWalk.addTree(newCommit.getTree());
            treeWalk.setRecursive(true);
            long totalSize = 0;
            while (treeWalk.next()) {
                FileMode mode = treeWalk.getFileMode(0);
                if (FileMode.SYMLINK.equals(mode)) {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, """
                            Symbolic links are not allowed: '%s'.
                            To fix this, remove the symbolic link and replace it with the actual file or directory.

                            - macOS/Linux:
                                 * run 'rm "%1$s"'
                            - Windows:
                                * If it's a file symlink: run 'del "%1$s"'
                                * If it's a directory symlink: run 'rmdir "%1$s"'

                            After removing the symlink, replace it with the correct file or folder, add it with 'git add', and commit the changes."""
                            .formatted(treeWalk.getPathString()));
                    return;
                }
                if (FileMode.GITLINK.equals(mode)) {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, """
                            Git submodules are not allowed: '%s'.
                            To remove the submodule from your repository:
                            1. Run: 'git rm --cached "%1$s"'
                            2. Delete the submodule directory: 'rm -rf "%1$s"' (macOS/Linux) or 'rmdir /s "%1$s"' (Windows)
                            3. Edit the '.gitmodules' file and remove the corresponding section.
                            4. Optionally, clean up '.git/config' if it contains a [submodule "%1$s"] section.
                            5. Commit all changes.""".formatted(treeWalk.getPathString()));
                    return;
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = reader.open(objectId);
                if (loader.getType() == Constants.OBJ_BLOB && loader.getSize() > MAX_BLOB_SIZE_BYTES) {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON,
                            "File '%s' exceeds 10MB size limit (%.2f MB)".formatted(treeWalk.getPathString(), loader.getSize() / (1024.0 * 1024.0)));
                    return;
                }
                if (loader.getType() == Constants.OBJ_BLOB) {
                    totalSize += loader.getSize();
                }
            }
            rejectOversizedRepository(repository, reader, revWalk, command, totalSize, maxRepositorySizeBytes);
        }
    }

    /**
     * Rejects a push that makes the files of the repository larger than the limit. A push that does not make the repository larger is accepted even if the repository is over
     * the limit already (e.g. because it was created from a large template before the limit existed), so that students can still push, and can bring the repository below the
     * limit.
     */
    private static void rejectOversizedRepository(Repository repository, ObjectReader reader, RevWalk revWalk, ReceiveCommand command, long totalSize, long maxRepositorySizeBytes)
            throws IOException {
        if (totalSize <= maxRepositorySizeBytes) {
            return;
        }
        long previousSize = ObjectId.zeroId().equals(command.getOldId()) ? largestExistingBranchSize(repository, reader, revWalk)
                : sizeOfFiles(reader, revWalk.parseCommit(command.getOldId()));
        if (totalSize <= previousSize) {
            return;
        }
        command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, """
                The repository would be %.1f MB large, but the limit is %d MB.
                Large files that never change (data sets, models, libraries, ...) must not be stored in git. Download them during the build instead.
                See %s
                To fix this, remove the large files from the repository, commit the changes and push again.""".formatted(totalSize / (1024.0 * 1024.0),
                maxRepositorySizeBytes / (1024 * 1024), REPOSITORY_SIZE_DOCUMENTATION_URL));
    }

    /**
     * The baseline for a push that creates a branch: the largest branch that exists already, or 0 in a repository without branches. Creating a branch at an existing commit
     * adds no files, so it must not be treated as growing the repository from nothing.
     */
    private static long largestExistingBranchSize(Repository repository, ObjectReader reader, RevWalk revWalk) throws IOException {
        long largest = 0;
        for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
            if (ref.getObjectId() != null) {
                largest = Math.max(largest, sizeOfFiles(reader, revWalk.parseCommit(ref.getObjectId())));
            }
        }
        return largest;
    }

    private static long sizeOfFiles(ObjectReader reader, RevCommit commit) throws IOException {
        long size = 0;
        try (TreeWalk treeWalk = new TreeWalk(reader)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                if (FileMode.GITLINK.equals(treeWalk.getFileMode(0))) {
                    // the object of a submodule lives in another repository
                    continue;
                }
                ObjectLoader loader = reader.open(treeWalk.getObjectId(0));
                if (loader.getType() == Constants.OBJ_BLOB) {
                    size += loader.getSize();
                }
            }
        }
        return size;
    }
}
