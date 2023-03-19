package de.tum.in.www1.artemis.security.localvc;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Contains an onPreReceive method that is called by JGit before a push is received (i.e. before the pushed files are written to disk but after the authorization check was
 * successful).
 */
public class LocalVCPrePushHook implements PreReceiveHook {

    /**
     * Called by JGit before a push is received (i.e. before the pushed files are written to disk but after the authorization check was successful).
     *
     * @param rp       the process handling the current receive. Hooks may obtain details about the destination repository through this handle.
     * @param commands unmodifiable set of valid commands still pending execution. May be the empty set.
     */
    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        Repository repository = rp.getRepository();

        Iterator<ReceiveCommand> iterator = commands.iterator();

        if (!iterator.hasNext()) {
            return;
        }

        ReceiveCommand command = iterator.next();

        // There should only be one command.
        if (iterator.hasNext()) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "There should only be one command.");
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
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot force push.");
                return;
            }

            // Prevent renaming branches.
            if (!command.getRefName().startsWith("refs/heads/")) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot rename a branch.");
                return;
            }

            git.close();
        }
        catch (IOException e) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "An error occurred while checking the branch.");
        }
    }
}
