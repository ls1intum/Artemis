package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.eclipse.jgit.http.server.GitServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.service.icl.ArtemisGitServletService;

/**
 * Configuration of the JGit Servlet that handles fetch and push requests for local Version Control.
 */
@Configuration
@Profile(PROFILE_LOCALVC)
public class JGitServletConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JGitServletConfiguration.class);

    private final ArtemisGitServletService artemisGitServlet;

    public JGitServletConfiguration(ArtemisGitServletService artemisGitServlet) {
        this.artemisGitServlet = artemisGitServlet;
    }

    /**
     * @return GitServlet (Git server implementation by JGit) configured with a repository resolver and filters for fetch and push requests.
     */
    @Bean
    public ServletRegistrationBean<GitServlet> jgitServlet() {
        log.info("Registering ArtemisGitServlet for handling fetch and push requests to [Artemis URL]/git/[Project Key]/[Repository Slug].git");
        return new ServletRegistrationBean<>(artemisGitServlet, "/git/*");
    }

}
