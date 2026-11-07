package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.localvc.service.GitRequestClassifier.GitRequestTarget;

/**
 * Admits every git HTTP request before JGit resolves the repository, so that repository existence can never be inferred
 * from the response.
 * <p>
 * JGit's repository resolver runs before the authentication filters, so a missing repository used to be answered (and,
 * crucially, throttled) differently from an existing one. This filter moves the pre-flight (missing credentials,
 * build-agent exemption, authentication rate limit) ahead of resolution: on rejection it writes a canonical response and
 * does not let the resolver run, and otherwise it records the {@link GitHandshakeDecision} so the downstream
 * authentication filters complete the request without repeating that work.
 */
public class LocalVCGitRequestAdmissionFilter extends OncePerRequestFilter {

    private final LocalVCServletService localVCServletService;

    public LocalVCGitRequestAdmissionFilter(LocalVCServletService localVCServletService) {
        this.localVCServletService = localVCServletService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        GitRequestTarget target = GitRequestClassifier.classify(request);
        if (target.kind() == GitRequestClassifier.Kind.MALFORMED) {
            // Not one of the four smart-HTTP combinations (dumb HTTP is disabled), so reject before resolving anything.
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return;
        }

        GitHandshakeDecision decision = localVCServletService.admitGitRequest(request, target.action());
        switch (decision) {
            case GitHandshakeDecision.Unauthenticated ignored -> {
                // Identical to the authentication filters' and the masking filter's 401: the Basic challenge and an
                // empty body, so a git client sends its credentials.
                response.setHeader("WWW-Authenticate", "Basic");
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
            }
            case GitHandshakeDecision.RateLimited rateLimited -> {
                // Identical for an existing and a missing repository, because this is decided before resolution. No
                // WWW-Authenticate: that header describes a missing credential, not throttling.
                response.setHeader("Retry-After", Long.toString(rateLimited.retryAfterSeconds()));
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            }
            case GitHandshakeDecision.BuildAgentClone ignored -> {
                request.setAttribute(LocalVCServletService.GIT_HANDSHAKE_DECISION_REQUEST_ATTRIBUTE, decision);
                filterChain.doFilter(request, response);
            }
            case GitHandshakeDecision.OrdinaryAuthentication ignored -> {
                request.setAttribute(LocalVCServletService.GIT_HANDSHAKE_DECISION_REQUEST_ATTRIBUTE, decision);
                filterChain.doFilter(request, response);
            }
        }
    }
}
