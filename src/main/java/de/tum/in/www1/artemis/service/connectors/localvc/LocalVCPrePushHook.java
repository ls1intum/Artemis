package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;

/**
 * Contains an onPreReceive method that is called by JGit before a push is received (i.e. before the pushed files are written to disk but after the authorization check was
 * successful).
 */
public class LocalVCPrePushHook implements PreReceiveHook {

    private final LocalVCServletService localVCServletService;

    private final HttpServletRequest request;

    public LocalVCPrePushHook(LocalVCServletService localVCServletService, HttpServletRequest request) {
        this.localVCServletService = localVCServletService;
        this.request = request;
    }

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
            defaultBranchName = localVCServletService.getDefaultBranchOfRepository(repository);
        }
        catch (LocalVCInternalException e) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "An error occurred while checking the branch.");
            return;
        }

        // Reject pushes to anything other than the default branch.
        if (!command.getRefName().equals("refs/heads/" + defaultBranchName)) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot push to a branch other than the default branch.");
            return;
        }

        try {
            Git git = new Git(repository);

            // Prevent deletion of branches.
            Ref ref = git.getRepository().exactRef(command.getRefName());
            if (ref != null && command.getNewId().equals(ObjectId.zeroId())) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot delete a branch.");
                return;
            }

            // Prevent force push.
            if (command.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
                try {
                    if (!localVCServletService.isUserAllowedToForcePush(request)) {
                        command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot force push.");
                        return;
                    }
                }
                catch (Exception e) {
                    command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "An error occurred while checking the user's permissions.");
                    return;
                }
            }

            git.close();
        }
        catch (IOException e) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "An error occurred while checking the branch.");
        }
    }
}
