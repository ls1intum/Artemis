package de.tum.in.www1.artemis.security.localvc;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCHookService;

public class LocalVCPostPushHook implements PostReceiveHook {

    private final LocalVCHookService localVCHookService;

    public LocalVCPostPushHook(LocalVCHookService localVCHookService) {
        this.localVCHookService = localVCHookService;
    }

    @Override
    public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) throws LocalVCBadRequestException {
        Iterator<ReceiveCommand> iterator = commands.iterator();

        if (!iterator.hasNext()) {
            return;
        }

        ReceiveCommand command = iterator.next();

        // There should only be one command.
        if (iterator.hasNext()) {
            command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "There should only be one command.");
        }

        if (command.getType() != ReceiveCommand.Type.UPDATE) {
            return;
        }

        String commitHash = command.getNewId().name();

        Repository repository = rp.getRepository();

        localVCHookService.createNewSubmission(commitHash, repository);
    }
}
