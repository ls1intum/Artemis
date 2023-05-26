package de.tum.in.www1.artemis.config.localvcci;

import java.util.Optional;

import org.eclipse.jgit.http.server.GitServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import de.tum.in.www1.artemis.service.connectors.localci.LocalCIConnectorService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCServletService;

/**
 * Configuration of the JGit Servlet that handles fetch and push requests for local Version Control.
 */
@Configuration
@Profile("localvc")
public class JGitServletConfiguration {

    private final Logger log = LoggerFactory.getLogger(JGitServletConfiguration.class);

    private final LocalVCServletService localVCServletService;

    private final Optional<LocalCIConnectorService> localCIConnectorService;

    public JGitServletConfiguration(LocalVCServletService localVCServletService, Optional<LocalCIConnectorService> localCIConnectorService) {
        this.localVCServletService = localVCServletService;
        this.localCIConnectorService = localCIConnectorService;
    }

    /**
     * @return GitServlet (Git server implementation by JGit) configured with a repository resolver and filters for fetch and push requests.
     */
    @Bean
    public ServletRegistrationBean<GitServlet> jgitServlet() {
        ArtemisGitServlet gitServlet = new ArtemisGitServlet(localVCServletService, localCIConnectorService);
        log.info("Registering ArtemisGitServlet for handling fetch and push requests to [Artemis URL]/git/[Project Key]/[Repository Slug].git");
        return new ServletRegistrationBean<>(gitServlet, "/git/*");
    }
}
