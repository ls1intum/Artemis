package de.tum.in.www1.artemis.security.localVC;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;

public class LocalVCPostPushHook implements PostReceiveHook {

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    private final LocalVCFilterUtilService localVCFilterUtilService;

    public LocalVCPostPushHook(LocalVCFilterUtilService localVCFilterUtilService) {
        this.localVCFilterUtilService = localVCFilterUtilService;
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

        LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(localVCPath, repositoryFolderPath);

        // For pushes to the "tests" repository, no submission is created.
        if (!localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.TESTS.getName())) {
            localVCFilterUtilService.createNewSubmission(commitHash, repository, localVCRepositoryUrl);
        }

    }
}
