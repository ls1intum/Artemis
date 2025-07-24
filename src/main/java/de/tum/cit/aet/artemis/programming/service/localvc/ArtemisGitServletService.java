package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCAuthException;

/**
 * This class configures the JGit Servlet, which is used to receive Git push and fetch requests for local VC.
 */
@Profile(PROFILE_LOCALVC)
@Lazy
@Service
public class ArtemisGitServletService extends GitServlet {

    private static final Logger log = LoggerFactory.getLogger(ArtemisGitServletService.class);

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
    @PostConstruct
    @Override
    public void init() throws ServletException {
        super.init();
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
            // we only need to set the PreReceiveHook and PostReceiveHook for authorized POST requests, as only these trigger onPreReceive or onPostReceive.
            // Before that, 2 GET requests to /info/refs?service=git-receive-pack are used to retrieve the refs and do not trigger the hooks.
            if (request.getMethod().equals(HttpMethod.POST.name()) && request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
                try {
                    String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    User user = localVCServletService.getUserByAuthHeader(authorizationHeader);
                    // Add a hook that prevents illegal actions on push (delete branch, rename branch, force push).
                    receivePack.setPreReceiveHook(new LocalVCPrePushHook(localVCServletService, user));
                    // Add a hook that triggers the creation of a new submission after the push went through successfully.
                    receivePack.setPostReceiveHook(new LocalVCPostPushHook(localVCServletService, user));
                }
                catch (LocalVCAuthException exception) {
                    log.error("Error while retrieving user from request header: {}", exception.getMessage());
                }
            }
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
