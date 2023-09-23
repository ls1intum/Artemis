package de.tum.in.www1.artemis.config.localvcci;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.transport.ReceivePack;

import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCFetchFilter;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCPostPushHook;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCPrePushHook;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCPushFilter;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCServletService;

/**
 * This class configures the JGit Servlet, which is used to receive Git push and fetch requests for local VC.
 */
public class ArtemisGitServlet extends GitServlet {

    /**
     * Constructor for ArtemisGitServlet.
     *
     * @param localVCServletService the service for authenticating and authorizing users and retrieving the repository from disk
     */
    public ArtemisGitServlet(LocalVCServletService localVCServletService) {
        this.setRepositoryResolver((req, name) -> {
            // req – the current request, may be used to inspect session state including cookies or user authentication.
            // name – name of the repository, as parsed out of the URL (everything after /git/).

            // Return the opened repository instance.
            return localVCServletService.resolveRepository(name);
        });

        // Add filters that every request to the JGit Servlet goes through, one for each fetch request, and one for each push request.
        this.addUploadPackFilter(new LocalVCFetchFilter(localVCServletService));
        this.addReceivePackFilter(new LocalVCPushFilter(localVCServletService));

        this.setReceivePackFactory((req, db) -> {
            ReceivePack receivePack = new ReceivePack(db);
            // Add a hook that prevents illegal actions on push (delete branch, rename branch, force push).
            receivePack.setPreReceiveHook(new LocalVCPrePushHook());
            // Add a hook that triggers the creation of a new submission after the push went through successfully.
            receivePack.setPostReceiveHook(new LocalVCPostPushHook(localVCServletService));
            return receivePack;
        });
    }
}
