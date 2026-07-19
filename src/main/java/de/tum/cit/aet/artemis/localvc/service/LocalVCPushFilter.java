package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Repository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.core.exception.HttpStatusException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuard;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Filters incoming push requests reaching the local Version Control implementation.
 */
public class LocalVCPushFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LocalVCPushFilter.class);

    private final LocalVCServletService localVCServletService;

    public LocalVCPushFilter(LocalVCServletService localVCServletService) {
        this.localVCServletService = localVCServletService;
    }

    /**
     * Filters incoming push requests performing authentication and authorization.
     */
    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, @NonNull FilterChain filterChain) throws IOException, ServletException {
        log.debug("Trying to push to repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            localVCServletService.authenticateAndAuthorizeGitRequest(servletRequest, RepositoryActionType.WRITE);
        }
        catch (LocalVCAuthException | LocalVCForbiddenException | LocalVCInternalException e) {
            servletResponse.setStatus(localVCServletService.getHttpStatusForException(e, servletRequest.getRequestURI()));
            return;
        }

        if (!"POST".equals(servletRequest.getMethod())) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        Repository repository = ServletUtils.getRepository(servletRequest);
        ProgrammingExercise exercise = (ProgrammingExercise) servletRequest.getAttribute(LocalVCServletService.AUTHORIZED_EXERCISE_ATTRIBUTE);
        final ProgrammingExerciseMutationGuard.MutationLease mutationLease;
        try {
            mutationLease = localVCServletService.claimProgrammingExerciseMutation(repository, exercise);
        }
        catch (HttpStatusException e) {
            servletResponse.sendError(e.getStatusCode().value(), e.getMessage() + " Please retry the push later.");
            return;
        }
        try (mutationLease) {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }
}
