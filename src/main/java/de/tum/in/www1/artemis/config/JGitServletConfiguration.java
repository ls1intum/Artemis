package de.tum.in.www1.artemis.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.jgitServlet.JGitFetchFilter;
import de.tum.in.www1.artemis.security.jgitServlet.JGitFilterUtilService;
import de.tum.in.www1.artemis.security.jgitServlet.JGitPushFilter;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * Configuration of the JGit Servlet that handles fetch and push requests for local Version Control.
 */
@Configuration
public class JGitServletConfiguration {

    private final Logger log = LoggerFactory.getLogger(JGitServletConfiguration.class);

    @Value("${artemis.local-git-server-path}")
    private String localGitPath;

    private final HashMap<String, Repository> repositories = new HashMap<>();

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final JGitFilterUtilService jGitFilterUtilService;

    public JGitServletConfiguration(JGitFilterUtilService jGitFilterUtilService, UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.jGitFilterUtilService = jGitFilterUtilService;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    @Bean
    public ServletRegistrationBean<GitServlet> jgitServlet() {

        try {
            GitServlet gitServlet = new GitServlet();
            gitServlet.setRepositoryResolver((req, name) -> {
                // req – the current request, may be used to inspect session state including cookies or user authentication.
                // name – name of the repository, as parsed out of the URL (everything after /git).
                // Returns the opened repository instance, never null.

                // Find the local repository depending on the name and return an opened instance. Must be closed later on.
                File gitDir = new File(localGitPath + File.separator + name);

                log.debug("Path to resolve repository from: {}", gitDir.getPath());
                if (!gitDir.exists()) {
                    log.debug("Could not find local repository with name {}", name);
                    throw new RepositoryNotFoundException(name);
                }

                Repository repository;

                if (repositories.containsKey(name)) {
                    log.debug("Retrieving cached local repository {}", name);
                    repository = repositories.get(name);
                }
                else {
                    log.debug("Opening local repository {}", name);
                    try {
                        repository = FileRepositoryBuilder.create(gitDir);
                        this.repositories.put(name, repository);
                    }
                    catch (IOException e) {
                        log.error("Unable to open local repository {}", name);
                        throw new RepositoryNotFoundException(name);
                    }
                }

                // Enable pushing without credentials, authentication is handled by the JGitPushFilter.
                repository.getConfig().setBoolean("http", null, "receivepack", true);

                // TODO: Check whether closing the repository via close() is necessary here or if I need to open it at all before returning.
                repository.incrementOpen();
                return repository;
            });

            gitServlet.addUploadPackFilter(new JGitFetchFilter(jGitFilterUtilService));
            gitServlet.addReceivePackFilter(new JGitPushFilter(jGitFilterUtilService));

            log.info("Registering GitServlet");
            return new ServletRegistrationBean<GitServlet>(gitServlet, "/git/*");
        }
        catch (Exception e) {
            log.error("Something went wrong creating the JGit Servlet.");
        }
        return null;
    }

}
