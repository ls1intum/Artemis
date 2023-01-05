package de.tum.in.www1.artemis.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
import org.springframework.context.annotation.Profile;

import de.tum.in.www1.artemis.security.localVC.LocalVCFetchFilter;
import de.tum.in.www1.artemis.security.localVC.LocalVCFilterUtilService;
import de.tum.in.www1.artemis.security.localVC.LocalVCPushFilter;

/**
 * Configuration of the JGit Servlet that handles fetch and push requests for local Version Control.
 */
@Configuration
@Profile("localvc")
public class JGitServletConfiguration {

    private final Logger log = LoggerFactory.getLogger(JGitServletConfiguration.class);

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    private final Map<String, Repository> repositories = new HashMap<>();

    private final LocalVCFilterUtilService localVCFilterUtilService;

    public JGitServletConfiguration(LocalVCFilterUtilService localVCFilterUtilService) {
        this.localVCFilterUtilService = localVCFilterUtilService;
    }

    /**
     * @return GitServlet (Git server implementation by JGit) configured with a repository resolver and filters for fetch and push requests.
     */
    @Bean
    public ServletRegistrationBean<GitServlet> jgitServlet() {

        try {
            GitServlet gitServlet = new GitServlet();
            gitServlet.setRepositoryResolver((req, name) -> {
                // req – the current request, may be used to inspect session state including cookies or user authentication.
                // name – name of the repository, as parsed out of the URL (everything after /git).
                // Returns the opened repository instance, never null.

                // Find the local repository depending on the name and return an opened instance. Must be closed later on.
                File gitDir = new File(localVCPath + File.separator + name);

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

                // Prevent force-pushes.
                repository.getConfig().setBoolean("receive", null, "denyNonFastForwards", true);

                // Prevent renaming branches.
                repository.getConfig().setBoolean("receive", null, "denyDeletes", true);
                repository.getConfig().setBoolean("receive", null, "denyRenames", true);

                return repository;
            });

            gitServlet.addUploadPackFilter(new LocalVCFetchFilter(localVCFilterUtilService));
            gitServlet.addReceivePackFilter(new LocalVCPushFilter(localVCFilterUtilService));

            log.info("Registering GitServlet");
            return new ServletRegistrationBean<>(gitServlet, "/git/*");
        }
        catch (Exception e) {
            log.error("Something went wrong creating the JGit Servlet.");
        }
        return null;
    }

}
