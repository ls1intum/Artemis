package de.tum.in.www1.artemis.security.localvc;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCPostPushHookService;

public class LocalVCPostPushHook implements PostReceiveHook {

    private final LocalVCPostPushHookService localVCPostPushHookService;

    public LocalVCPostPushHook(LocalVCPostPushHookService localVCPostPushHookService) {
        this.localVCPostPushHookService = localVCPostPushHookService;
    }

    @Override
    public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) throws LocalVCBadRequestException {
        Iterator<ReceiveCommand> iterator = commands.iterator();

        if (!iterator.hasNext()) {
            throw new LocalVCBadRequestException();
        }

        ReceiveCommand command = iterator.next();

        // There should only be one command.
        if (iterator.hasNext()) {
            throw new LocalVCBadRequestException();
        }

        if (command.getType() != ReceiveCommand.Type.UPDATE) {
            throw new LocalVCBadRequestException();
        }

        String commitHash = command.getNewId().name();

        Repository repository = rp.getRepository();

        localVCPostPushHookService.createNewSubmission(commitHash, repository);
    }
}
