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

    @Override
    public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        Iterator<ReceiveCommand> iterator = commands.iterator();

        // There should at least be one command.
        if (!iterator.hasNext()) {
            return;
        }

        ReceiveCommand command = iterator.next();

        // There should only be one command.
        if (iterator.hasNext()) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "There should only be one command.");
            return;
        }

        if (command.getType() != ReceiveCommand.Type.UPDATE) {
            // The command can also be of type CREATE (e.g. when creating a new branch). This will never lead to a new submission.
            // Pushes for submissions must come from the default branch, which can only be updated and not created by the student.
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "Only update commands are taken into consideration for submissions.");
            return;
        }

        String commitHash = command.getNewId().name();

        Repository repository = rp.getRepository();

        localCIPushService.orElseThrow().processNewPush(commitHash, repository);
    }
}
