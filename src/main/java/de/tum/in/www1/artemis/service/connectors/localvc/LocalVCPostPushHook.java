package de.tum.in.www1.artemis.service.connectors.localvc;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCIConnectorService;

/**
 * Contains an onPostReceive method that is called by JGit after a push has been received (i.e. after the pushed files were successfully written to disk).
 */
public class LocalVCPostPushHook implements PostReceiveHook {

    private final Logger log = LoggerFactory.getLogger(LocalVCPostPushHook.class);

    private final Optional<LocalCIConnectorService> localCIConnectorService;

    public LocalVCPostPushHook(Optional<LocalCIConnectorService> localCIConnectorService) {
        this.localCIConnectorService = localCIConnectorService;
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

        try {
            localCIConnectorService.orElseThrow().processNewPush(commitHash, repository);
        }
        catch (LocalCIException e) {
            // Cannot set an error message to be displayed to the user in the PostReceiveHook.
            // Throwing an exception here would cause the push to get stuck.
            // The user will see in the UI that no build was executed.
            log.error("Error while processing new push to repository {}", repository.getDirectory(), e);
        }
    }
}
