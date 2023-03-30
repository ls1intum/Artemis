package de.tum.in.www1.artemis.config;

import java.util.Optional;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.transport.ReceivePack;

import de.tum.in.www1.artemis.security.localvc.LocalVCFetchFilter;
import de.tum.in.www1.artemis.security.localvc.LocalVCFilterService;
import de.tum.in.www1.artemis.security.localvc.LocalVCPostPushHook;
import de.tum.in.www1.artemis.security.localvc.LocalVCPrePushHook;
import de.tum.in.www1.artemis.security.localvc.LocalVCPushFilter;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCIPushService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCServletService;

public class ArtemisGitServlet extends GitServlet {

    public ArtemisGitServlet(LocalVCServletService localVCServletService, LocalVCFilterService localVCFilterService, Optional<LocalCIPushService> localCIPushService) {
        super();

        this.setRepositoryResolver((req, name) -> {
            // req – the current request, may be used to inspect session state including cookies or user authentication.
            // name – name of the repository, as parsed out of the URL (everything after /git).

            // Return the opened repository instance.
            return localVCServletService.resolveRepository(name);
        });

        // Add filters that every request to the JGit Servlet goes through, one for each fetch request, and one for each push request.
        this.addUploadPackFilter(new LocalVCFetchFilter(localVCFilterService));
        this.addReceivePackFilter(new LocalVCPushFilter(localVCFilterService));

        this.setReceivePackFactory((req, db) -> {
            ReceivePack receivePack = new ReceivePack(db);
            // Add a hook that prevents illegal actions on push (delete branch, rename branch, force push).
            receivePack.setPreReceiveHook(new LocalVCPrePushHook());
            // Add a hook that triggers the creation of a new submission after the push went through successfully.
            receivePack.setPostReceiveHook(new LocalVCPostPushHook(localCIPushService));
            return receivePack;
        });
    }
}
