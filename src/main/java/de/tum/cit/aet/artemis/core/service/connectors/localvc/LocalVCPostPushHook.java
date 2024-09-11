package de.tum.cit.aet.artemis.core.service.connectors.localvc;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;

/**
 * Contains an onPostReceive method that is called by JGit after a push has been received (i.e. after the pushed files were successfully written to disk).
 */
public class LocalVCPostPushHook implements PostReceiveHook {

    private final LocalVCServletService localVCServletService;

    public LocalVCPostPushHook(LocalVCServletService localVCServletService) {
        this.localVCServletService = localVCServletService;
    }

    /**
     * Called by JGit after a push has been received (i.e. after the pushed files were successfully written to disk).
     *
     * @param receivePack
     *                        the process handling the current receive. Hooks may obtain
     *                        details about the destination repository through this handle.
     * @param commands
     *                        unmodifiable set of successfully completed commands.
     */
    @Override
    public void onPostReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {

        // The PreReceiveHook already checked that there is exactly one command and rejects the push otherwise.
        Iterator<ReceiveCommand> iterator = commands.iterator();
        if (!iterator.hasNext()) {
            // E.g. no refs were updated during the push operation.
            // Note: This was already checked in the LocalVCPrePushHook but the request is then just delegated further and is finally stopped here.
            return;
        }

        ReceiveCommand command = iterator.next();

        String wrongBranchMessage = "Only pushes to the default branch will be graded. Your changes were saved nonetheless.";

        if (command.getType() != ReceiveCommand.Type.UPDATE && command.getType() != ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
            // The command can also be of type CREATE (e.g. when creating a new branch). This will never lead to a new submission.
            // Pushes for submissions must come from the default branch, which can only be updated and not created by the student.
            // Updates to other branches will be caught in the catch block below, returning an error message to the user.
            receivePack.sendError(wrongBranchMessage);
            return;
        }

        // If there are multiple commits in the push, this will always retrieve the commit hash of the latest commit.
        String commitHash = command.getNewId().name();

        Repository repository = receivePack.getRepository();

        try {
            localVCServletService.processNewPush(commitHash, repository);
        }
        catch (LocalCIException e) {
            // Return an error message to the user.
            receivePack.sendError(
                    "Something went wrong while processing your push. Your changes were saved, but we could not test your submission. Please try again and if this issue persists, contact the course administrators.");
        }
        catch (VersionControlException e) {
            receivePack.sendError(wrongBranchMessage);
        }
    }
}
