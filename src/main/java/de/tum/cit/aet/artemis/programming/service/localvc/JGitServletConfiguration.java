package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import jakarta.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Configuration of the JGit Servlet that handles fetch and push requests for local Version Control.
 */
@Configuration(proxyBeanMethods = false)
@Lazy
@Profile(PROFILE_LOCALVC)
public class JGitServletConfiguration implements ServletContextInitializer {

    private static final Logger log = LoggerFactory.getLogger(JGitServletConfiguration.class);

    private final ArtemisGitServletService artemisGitServlet;

    public JGitServletConfiguration(@Lazy ArtemisGitServletService artemisGitServlet) {
        this.artemisGitServlet = artemisGitServlet;
    }

    @Override
    public void onStartup(ServletContext servletContext) {
        log.info("Dynamically registering GitServlet under /git/*");
        log.info("Registering ArtemisGitServlet for handling fetch and push requests to [Artemis URL]/git/[Project Key]/[Repository Slug].git");
        servletContext.addServlet("gitServlet", artemisGitServlet).addMapping("/git/*");
    }
}
