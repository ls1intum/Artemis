package de.tum.cit.aet.artemis.programming.service.localvc;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import jakarta.validation.constraints.NotNull;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;

/**
 * Contains an onPreReceive method that is called by JGit before a push is received (i.e. before the pushed files are written to disk but after the authorization check was
 * successful).
 */
public record LocalVCPrePushHook(LocalVCServletService localVCServletService, User user) implements PreReceiveHook {

    private static final Logger log = LoggerFactory.getLogger(LocalVCPrePushHook.class);

    public LocalVCPrePushHook(LocalVCServletService localVCServletService, @NotNull User user) {
        this.localVCServletService = localVCServletService;
        this.user = user;
    }

    private static final long MAX_BLOB_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    /**
     * Called by JGit before a push is received (i.e. before the pushed files are written to disk but after the authorization check was successful).
     *
     * @param receivePack the process handling the current receive. Hooks may obtain details about the destination repository through this handle.
     * @param commands    ReceiveCommands that each correspond to a single ref update (e.g. update to a branch or a tag).
     */
    @Override
    public void onPreReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {

        Iterator<ReceiveCommand> iterator = commands.iterator();
        if (!iterator.hasNext()) {
            // E.g. no refs were updated during the push operation.
            // Note: There is no command that we can set a REJECTED Result on so this will continue into the LocalVCPostPushHook and we have to return there again.
            return;
        }

        ReceiveCommand command = iterator.next();

        // There should only be one ReceiveCommand. If there are multiple, e.g. because the user pushes to multiple branches simultaneously, we reject the push.
        if (iterator.hasNext()) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot push multiple refs at once.");
            return;
        }

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

            rejectFilesLargerThan10MbOrSymlinksOrSubModules(repository, command);
        }
        catch (IOException e) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "An error occurred while checking the branch.");
        }
    }

    private static void rejectFilesLargerThan10MbOrSymlinksOrSubModules(Repository repository, ReceiveCommand command) throws IOException {
        try (ObjectReader reader = repository.newObjectReader(); RevWalk revWalk = new RevWalk(reader); TreeWalk treeWalk = new TreeWalk(reader)) {
            RevCommit newCommit = revWalk.parseCommit(command.getNewId());
            treeWalk.addTree(newCommit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                FileMode mode = treeWalk.getFileMode(0);
                if (FileMode.SYMLINK.equals(mode)) {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, String.format("""
                            Symbolic links are not allowed: '%s'.
                            To fix this, remove the symbolic link and replace it with the actual file or directory.

                            - macOS/Linux:
                                 * run 'rm "%1$s"'
                            - Windows:
                                * If it's a file symlink: run 'del "%1$s"'
                                * If it's a directory symlink: run 'rmdir "%1$s"'

                            After removing the symlink, replace it with the correct file or folder, add it with 'git add', and commit the changes.""", treeWalk.getPathString()));
                    break;
                }
                if (FileMode.GITLINK.equals(mode)) {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, String.format("""
                            Git submodules are not allowed: '%s'.
                            To remove the submodule from your repository:
                            1. Run: 'git rm --cached "%1$s"'
                            2. Delete the submodule directory: 'rm -rf "%1$s"' (macOS/Linux) or 'rmdir /s "%1$s"' (Windows)
                            3. Edit the '.gitmodules' file and remove the corresponding section.
                            4. Optionally, clean up '.git/config' if it contains a [submodule "%1$s"] section.
                            5. Commit all changes.""", treeWalk.getPathString()));
                    break;
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = reader.open(objectId);
                if (loader.getType() == Constants.OBJ_BLOB && loader.getSize() > MAX_BLOB_SIZE_BYTES) {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON,
                            String.format("File '%s' exceeds 10MB size limit (%.2f MB)", treeWalk.getPathString(), loader.getSize() / (1024.0 * 1024.0)));
                    break;
                }
            }
        }
    }
}
