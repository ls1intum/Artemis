package de.tum.in.www1.artemis.config;

import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.jgitServlet.JGitFetchFilter;
import de.tum.in.www1.artemis.security.jgitServlet.JGitPushFilter;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

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

    private final UserDetailsService userDetailsService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExerciseRepository exerciseRepository;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    public JGitServletConfiguration(UserRepository userRepository, UserDetailsService userDetailsService, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, ExerciseRepository exerciseRepository, AuthenticationManagerBuilder authenticationManagerBuilder) {
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.exerciseRepository = exerciseRepository;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
    }

    @Bean
    public ServletRegistrationBean<GitServlet> jgitServlet(ApplicationContext applicationContext) {

        try {
            GitServlet gs = new GitServlet();
            gs.setRepositoryResolver((req, name) -> {
                // req – the current request, may be used to inspect session state including cookies or user authentication.
                // name – name of the repository, as parsed out of the URL.
                // Returns the opened repository instance, never null.

                // Find the local repository depending on the name and return an opened instance. Must be closed later on.
                File gitDir = new File(localGitPath + File.separator + name + ".git");

                log.debug("Path to resolve repository from: {}", gitDir.getPath());
                if (!gitDir.exists()) {
                    log.debug("Could not find local repository with name {}", name);
                    throw new RepositoryNotFoundException(name);
                }

                Repository repository;

                if (repositories.containsKey(name)) {
                    log.debug("Retrieving cached local repository {}", name);
                    repository = repositories.get(name);
                } else {
                    log.debug("Opening local repository {}", name);
                    try {
                        repository = FileRepositoryBuilder.create(gitDir);
                        this.repositories.put(name, repository);
                    } catch (IOException e) {
                        log.error("Unable to open local repository {}", name);
                        throw new RepositoryNotFoundException(name);
                    }
                }

                repository.getConfig().setBoolean("http", null, "receivepack", true);

                repository.incrementOpen(); // hier nochmal checken ob ein close() notwendig ist.
                return repository;
            });

            gs.addUploadPackFilter(new JGitFetchFilter(userRepository, userDetailsService, courseRepository, authorizationCheckService, exerciseRepository, authenticationManagerBuilder));
            gs.addReceivePackFilter(new JGitPushFilter(userRepository, authorizationCheckService));

            log.info("Registering GitServlet");
            return new ServletRegistrationBean<GitServlet>(gs, "/git/*");

        } catch (Exception e) {
            log.error("Something went wrong creating the JGit Servlet.");
        }
        return null;
    }

}
