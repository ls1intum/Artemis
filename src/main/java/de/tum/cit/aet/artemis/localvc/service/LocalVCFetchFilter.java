package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;
import java.time.ZonedDateTime;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Filters incoming fetch requests reaching the local git server implementation.
 */
public class LocalVCFetchFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LocalVCFetchFilter.class);

    private final LocalVCServletService localVCServletService;

    private final LocalVCAccessLogRecorderService accessLogRecorder;

    private final LocalVCUsageTrackingService usageTrackingService;

    public LocalVCFetchFilter(LocalVCServletService localVCServletService, LocalVCAccessLogRecorderService accessLogRecorder, LocalVCUsageTrackingService usageTrackingService) {
        this.localVCServletService = localVCServletService;
        this.accessLogRecorder = accessLogRecorder;
        this.usageTrackingService = usageTrackingService;
    }

    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, @NonNull FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to fetch repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            localVCServletService.authenticateAndAuthorizeGitRequest(servletRequest, RepositoryActionType.READ);
        }
        catch (LocalVCAuthException | LocalVCForbiddenException | LocalVCInternalException e) {
            int status = localVCServletService.getHttpStatusForException(e, servletRequest.getRequestURI());
            // Parts of the git authentication handshake are expected and happen on every clone, so they must not be
            // logged as warnings. The exception itself says whether it is such a case; matching on the message text
            // silently missed the second one (an empty password) and made every clone look like a failure. Log every
            // other rejection with its concrete reason, otherwise a 401/403 is returned silently and cannot be diagnosed.
            if (!(e instanceof LocalVCAuthException authException && authException.isExpectedDuringHandshake())) {
                log.warn("LocalVC fetch rejected for {} -> HTTP {} ({}: {})", servletRequest.getRequestURI(), status, e.getClass().getSimpleName(), e.getMessage());
            }
            servletResponse.setStatus(status);
            return;
        }
        catch (RateLimitExceededException e) {
            // The git servlet runs outside Spring MVC, so the ExceptionTranslator never sees this exception: answered
            // here, or it leaves the servlet uncaught and the client gets a generic error instead of 429. The rate limit
            // service has already logged the rejection with the client address.
            log.debug("LocalVC fetch rate limited for {}, retry after {} seconds", servletRequest.getRequestURI(), e.getRetryAfterSeconds());
            servletResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            if (e.getRetryAfterSeconds() > 0) {
                servletResponse.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
            }
            return;
        }
        catch (AuthenticationException e) {
            // the failed attempt itself was written to the VCS access log where the credentials were checked
            log.warn("LocalVC fetch authentication failed for {} ({}: {})", servletRequest.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }

        long startNanos = System.nanoTime();
        // The request that advertises the references is the first of every fetch, and all there is of one that is up to date.
        boolean advertisement = "GET".equals(servletRequest.getMethod());
        String commitHashBefore = advertisement ? LocalVCAccessLogRecorderService.headCommitOf(servletRequest) : null;
        // The entry of the advertisement is written after its response was sent, so it carries the time of the request, not the time of the write
        ZonedDateTime requestStarted = ZonedDateTime.now();
        // stays true if doFilter throws, so a fetch that blew up is not counted as a success
        boolean failed = true;
        try {
            filterChain.doFilter(servletRequest, servletResponse);
            failed = servletResponse.getStatus() >= HttpServletResponse.SC_BAD_REQUEST;
            if (advertisement && !failed) {
                accessLogRecorder.auditOf(servletRequest).onAccessed(commitHashBefore, requestStarted);
            }
        }
        finally {
            usageTrackingService.recordFetch(servletRequest, (System.nanoTime() - startNanos) / 1_000_000, failed);
            // the request is over, so everything about it is known: it is written if it sent data, or if it advertised the references
            accessLogRecorder.recordFetchHTTPS(servletRequest);
        }
    }
}
