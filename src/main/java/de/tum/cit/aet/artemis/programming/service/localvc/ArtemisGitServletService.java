package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.FullStartupEvent;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * This class configures the JGit Servlet, which is used to receive Git push and fetch requests for local VC.
 */
@Profile(PROFILE_LOCALVC)
@Lazy
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
     * Sets the pre/post receive/upload hooks.
     * <p>
     * For general information on the different hooks and git packs see the git documentation:
     * <p>
     * <a href="https://git-scm.com/docs/git-receive-pack">https://git-scm.com/docs/git-receive-pack</a>
     * <p>
     * <a href="https://git-scm.com/docs/git-upload-pack">https://git-scm.com/docs/git-upload-pack</a>
     */
    @EventListener(FullStartupEvent.class)
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
            // the user inside the request is always null here
            var user = (User) request.getAttribute("user");
            receivePack.setPreReceiveHook(new LocalVCPrePushHook(localVCServletService, user));
            // Add a hook that triggers the creation of a new submission after the push went through successfully.
            receivePack.setPostReceiveHook(new LocalVCPostPushHook(localVCServletService, user));
            return receivePack;
        });

        this.setUploadPackFactory((request, repository) -> {
            UploadPack uploadPack = new UploadPack(repository);

            // Add the custom pre-upload hook, to distinguish between clone and pull operations
            uploadPack.setPreUploadHook(new LocalVCFetchPreUploadHook(localVCServletService, request));
            return uploadPack;
        });
    }

}
