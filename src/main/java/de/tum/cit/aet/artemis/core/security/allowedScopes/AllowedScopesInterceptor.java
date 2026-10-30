package de.tum.cit.aet.artemis.core.security.allowedScopes;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Denies requests authenticated with a personal access token unless the endpoint opted in with {@link AllowedScopes} and
 * the token holds every scope the endpoint requires. Requests that are not token-authenticated pass untouched.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class AllowedScopesInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof PersonalAccessTokenAuthentication tokenAuthentication)) {
            return true;
        }

        AllowedScopes allowedScopes = handler instanceof HandlerMethod handlerMethod ? handlerMethod.getMethod().getAnnotation(AllowedScopes.class) : null;
        if (allowedScopes == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "This endpoint cannot be called with a personal access token.");
            return false;
        }

        boolean holdsAllScopes = Arrays.stream(allowedScopes.value()).allMatch(tokenAuthentication.getScopes()::contains);
        if (!holdsAllScopes) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "The personal access token lacks a scope required by this endpoint.");
            return false;
        }
        return true;
    }
}
