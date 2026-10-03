package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.nio.file.Path;

import org.eclipse.jgit.http.server.GitServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

/**
 * Configuration of the JGit Servlet that handles fetch and push requests for local Version Control.
 */
@Configuration
@Profile(PROFILE_LOCALVC)
@Lazy
public class JGitServletConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JGitServletConfiguration.class);

    private final ArtemisGitServletService artemisGitServlet;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    public JGitServletConfiguration(ArtemisGitServletService artemisGitServlet) {
        this.artemisGitServlet = artemisGitServlet;
    }

    /**
     * @return GitServlet (Git server implementation by JGit) configured with a repository resolver and filters for fetch and push requests.
     */
    @Bean
    public ServletRegistrationBean<GitServlet> jgitServlet() {
        log.debug("Registering ArtemisGitServlet for handling fetch and push requests to [Artemis URL]/git/[Project Key]/[Repository Slug].git");
        ServletRegistrationBean<GitServlet> registration = new ServletRegistrationBean<>(artemisGitServlet, "/git/*");
        // REQUIRED: set base-path to the root folder of bare repositories
        registration.addInitParameter("base-path", localVCBasePath.toAbsolutePath().toString());

        // OPTIONAL: allow access to all repos, otherwise must whitelist
        registration.addInitParameter("export-all", "true");
        return registration;
    }

    /**
     * Registers the filter that normalises every 401 response of the git server to a single shape, so that a missing
     * repository cannot be told apart from an existing but forbidden one via the response headers or body. It is mapped
     * to the same path as the git servlet and ordered before it, so it wraps the response the servlet writes to.
     *
     * @return the registration of the {@link LocalVCAuthenticationResponseMaskingFilter}.
     */
    @Bean
    public FilterRegistrationBean<LocalVCAuthenticationResponseMaskingFilter> localVCAuthenticationResponseMaskingFilter() {
        FilterRegistrationBean<LocalVCAuthenticationResponseMaskingFilter> registration = new FilterRegistrationBean<>(new LocalVCAuthenticationResponseMaskingFilter());
        registration.addUrlPatterns("/git/*");
        // Run as the outermost filter for git requests so it wraps the response before any downstream filter or the git
        // servlet can commit a 401.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

}
