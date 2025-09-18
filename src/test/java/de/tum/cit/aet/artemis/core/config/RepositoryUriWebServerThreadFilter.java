package de.tum.cit.aet.artemis.core.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.programming.service.RepositoryUriConversionUtil;

/**
 * Servlet filter that temporarily overrides the server base URL used by
 * {@link RepositoryUriConversionUtil} for the duration of a single HTTP request
 * to Artemis’ embedded Git endpoints.
 *
 * <p>
 * <strong>Why this exists</strong><br>
 * In tests and in local deployments Artemis stores “short” repository URIs in the database
 * (e.g. {@code /git/PRJ/PRJ-student1.git}). Whenever code needs a “full” URI
 * (e.g. {@code http://localhost:49152/git/PRJ/PRJ-student1.git}), it expands the short form
 * using a thread-local “server base URL” inside {@code RepositoryUriConversionUtil}.
 *
 * <p>
 * When Git clients (JGit) hit Artemis’ real HTTP endpoints (under {@code /git/**}),
 * this filter computes the effective base URL from the incoming request
 * (scheme, server name, and port) and overrides the thread-local base just for this request.
 * This ensures that any short-to-full URI expansion performed while handling the request
 * uses the correct host and port of the currently running server, even when multiple test contexts
 * use different {@code server.port} values.
 * </p>
 *
 * @see RepositoryUriConversionUtil
 * @see OncePerRequestFilter
 */
@Lazy
@Profile(SPRING_PROFILE_TEST)
@Configuration
public class RepositoryUriWebServerThreadFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RepositoryUriWebServerThreadFilter.class);

    private static final String GIT_PREFIX = "/git/";

    @Value("${artemis.version-control.url}")
    private String baseUrl;

    /**
     * Overrides the thread-local base URL for {@link RepositoryUriConversionUtil}
     * if and only if the current request targets a Git HTTP endpoint ({@code /git/**}).
     *
     * <p>
     * The base URL is derived from the request’s scheme, server name, and server port
     * (e.g. {@code http://localhost:49152}). The override is applied for the duration of the
     * filter chain and cleared afterwards.
     * </p>
     *
     * @param request  the current HTTP request
     * @param response the current HTTP response
     * @param chain    the filter chain
     * @throws ServletException    if the filter chain throws it
     * @throws java.io.IOException if the filter chain throws it
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, java.io.IOException {

        final String uri = request.getRequestURI();

        // Only necessary for real HTTP git traffic (e.g. JGit). Calls to all other endpoints are MockMVC-based and skipped.
        if (!uri.startsWith(GIT_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        final String base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        log.debug("Setting RepositoryUriConversionUtil base URL override to {} for request {}", baseUrl, uri);

        RepositoryUriConversionUtil.overrideServerUrlForCurrentThread(baseUrl);
        try {
            chain.doFilter(request, response);
        }
        finally {
            RepositoryUriConversionUtil.clearServerUrlOverrideForCurrentThread();
        }
    }
}
