package de.tum.cit.aet.artemis.core.security.filter;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_PRODUCTION;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({ SPRING_PROFILE_PRODUCTION, SPRING_PROFILE_TEST })
@Component
public class ApiVersionFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionFilter.class);

    public static final String CONTENT_VERSION_HEADER = "Content-Version";

    /**
     * Artemis Version as defined in build.gradle
     */
    @Value("${artemis.version}")
    private String VERSION;

    /**
     * Use doFilter to hook into every HTTP Request and set Content-Version HTTP Header to the Artemis Version.
     * Also send the current Server Time used for syncing the client
     *
     * @param request  the <code>ServletRequest</code> object contains the client's request
     * @param response the <code>ServletResponse</code> object contains the filter's response
     * @param chain    the <code>FilterChain</code> for invoking the next filter or the resource
     * @throws IOException      if an I/O related error has occurred during the processing
     * @throws ServletException if an exception occurs that interferes with the filter's normal operation
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final HttpServletResponse httpResponse = (HttpServletResponse) response;

        log.debug("Adding Version to Request {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());

        httpResponse.addHeader(CONTENT_VERSION_HEADER, VERSION);

        chain.doFilter(request, response);
    }

}
