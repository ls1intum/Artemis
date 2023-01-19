package de.tum.in.www1.artemis.security.localVC;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

public class LocalVCPostPushHook implements PostReceiveHook {

    private final LocalVCFilterService localVCFilterService;

    public LocalVCPostPushHook(LocalVCFilterService localVCFilterService) {
        this.localVCFilterService = localVCFilterService;
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

        File repositoryFolderPath = repository.getDirectory();

        localVCFilterService.createNewSubmission(commitHash, repository, repositoryFolderPath);
    }
}
