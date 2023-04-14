package de.tum.in.www1.artemis.security.localvc;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import de.tum.in.www1.artemis.service.connectors.localci.LocalCIPushService;

/**
 * Contains an onPostReceive method that is called by JGit after a push has been received (i.e. after the pushed files were successfully written to disk).
 */
public class LocalVCPostPushHook implements PostReceiveHook {

    private final Optional<LocalCIPushService> localCIPushService;

    public LocalVCPostPushHook(Optional<LocalCIPushService> localCIPushService) {
        this.localCIPushService = localCIPushService;
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

        if (command.getType() != ReceiveCommand.Type.UPDATE) {
            // The command can also be of type CREATE (e.g. when creating a new branch). This will never lead to a new submission.
            // Pushes for submissions must come from the default branch, which can only be updated and not created by the student.
            return;
        }

        String commitHash = command.getNewId().name();

        Repository repository = receivePack.getRepository();

        localCIPushService.orElseThrow().processNewPush(commitHash, repository);
    }
}
