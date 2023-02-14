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

public class LocalVCPrePushHook implements PreReceiveHook {

    public LocalVCPrePushHook() {
    }

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
        }

        try {
            Git git = new Git(repository);

            // Prevent deletion of branches.
            Ref ref = git.getRepository().exactRef(command.getRefName());
            if (ref != null && command.getNewId().equals(ObjectId.zeroId())) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot delete a branch.");
            }

            // Prevent force push.
            if (command.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot force push.");
            }

            // Prevent renaming branches.
            if (!command.getRefName().startsWith("refs/heads/")) {
                command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "You cannot rename a branch.");
            }

            git.close();
        }
        catch (IOException e) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "An error occurred while checking the branch.");
        }
    }
}
