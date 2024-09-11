package de.tum.cit.aet.artemis.service.icl;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_LOCALVC;

import jakarta.annotation.PostConstruct;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.transport.ReceivePack;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCFetchFilter;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCPostPushHook;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCPrePushHook;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCPushFilter;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCServletService;

/**
 * This class configures the JGit Servlet, which is used to receive Git push and fetch requests for local VC.
 */
@Profile(PROFILE_LOCALVC)
@Service
public class ArtemisGitServletService extends GitServlet {

    private final LocalVCServletService localVCServletService;

    /**
     * Constructor for ArtemisGitServlet.
     *
     * @param localVCServletService the service for authenticating and authorizing users and retrieving the repository from disk
     */
    public ArtemisGitServletService(LocalVCServletService localVCServletService) {
        this.localVCServletService = localVCServletService;
    }

    /**
     * Initialize the ArtemisGitServlet by setting the repository resolver and adding filters for fetch and push requests.
     */
    @PostConstruct
    @Override
    public void init() {
        this.setRepositoryResolver((request, name) -> {
            // request – the current request, may be used to inspect session state including cookies or user authentication.
            // name – name of the repository, as parsed out of the URL (everything after /git/).

            // Return the opened repository instance.
            return localVCServletService.resolveRepository(name);
        });

        // Add filters that every request to the JGit Servlet goes through, one for each fetch request, and one for each push request.
        this.addUploadPackFilter(new LocalVCFetchFilter(localVCServletService));
        this.addReceivePackFilter(new LocalVCPushFilter(localVCServletService));

        this.setReceivePackFactory((request, repository) -> {
            ReceivePack receivePack = new ReceivePack(repository);
            // Add a hook that prevents illegal actions on push (delete branch, rename branch, force push).
            receivePack.setPreReceiveHook(new LocalVCPrePushHook(localVCServletService, (User) request.getAttribute("user")));
            // Add a hook that triggers the creation of a new submission after the push went through successfully.
            receivePack.setPostReceiveHook(new LocalVCPostPushHook(localVCServletService));
            return receivePack;
        });
    }
}
