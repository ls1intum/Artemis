package de.tum.cit.aet.artemis.core.security.jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceSuperAdmin;

/**
 * Answers whether a request is served by a handler carrying {@link EnforceAdmin} or {@link EnforceSuperAdmin}.
 *
 * <p>
 * {@link JWTFilter} needs this to decide whether to keep the administrator authorities on a request. It must keep them
 * for an explicit administrator endpoint, whose annotation performs the persisted-role and passkey checks itself and
 * produces the structured passkey error the client acts on, and it must remove them everywhere else, because a normal
 * endpoint's ordinary role checks deliberately do not invoke administrator endpoint security.
 *
 * <p>
 * The question is answered from the mappings Spring actually registered rather than from the shape of the path. A path
 * heuristic looked simpler but was wrong in both directions: it missed {@code /api/exam/rooms/admin/**},
 * {@code /api/account/passkeys/{id}/approval} and the bonus calculation endpoint, all annotated but not laid out as
 * {@code /api/<module>/admin/...}, and any prefix wide enough to cover the last two would have exempted their
 * ordinary neighbours as well. Matching the registered mappings has neither problem, and an administrator endpoint
 * added later is covered without anyone remembering this class exists.
 *
 * <p>
 * The HTTP method is part of the answer, not just the path. {@code GET /api/account/passkeys/admin} is an
 * administrator endpoint while {@code PUT} and {@code DELETE} on the same path are the ordinary
 * {@code /api/account/passkeys/{passkeyId}} handlers, so a path-only match would keep the administrator authority on
 * requests no administrator endpoint serves. Spring rejects two handlers sharing a path and a method, so path plus
 * method identifies the handler.
 */
public class ExplicitAdministratorApiMatcher {

    private static final Logger log = LoggerFactory.getLogger(ExplicitAdministratorApiMatcher.class);

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    /**
     * Resolved on first use rather than in the constructor: this collaborator is built while the security filter chain
     * is, which is before the handler mappings exist. Written once and read without synchronisation afterwards, so a
     * race can only cost a second scan.
     */
    private volatile List<RegisteredAdministratorEndpoint> administratorEndpoints;

    public ExplicitAdministratorApiMatcher(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    /**
     * @param request the request being filtered
     * @return whether an administrator endpoint serves it, and its administrator authorities therefore have to survive
     *         the filter
     */
    public boolean matches(HttpServletRequest request) {
        List<RegisteredAdministratorEndpoint> endpoints = administratorEndpoints();
        if (endpoints.isEmpty()) {
            return false;
        }
        PathContainer path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
        return endpoints.stream().anyMatch(endpoint -> endpoint.matches(path, request.getMethod()));
    }

    private List<RegisteredAdministratorEndpoint> administratorEndpoints() {
        List<RegisteredAdministratorEndpoint> resolved = administratorEndpoints;
        if (resolved != null) {
            return resolved;
        }
        resolved = collectRegisteredAdministratorEndpoints();
        if (resolved.isEmpty()) {
            // Nothing to cache yet: either the handler mappings are not built, or this node serves no web endpoints at
            // all. Retrying costs a scan on the next request and avoids caching an answer that is only true for now.
            log.debug("No administrator endpoints found yet, not caching the result");
            return List.of();
        }
        administratorEndpoints = resolved;
        log.debug("Found {} administrator endpoints", resolved.size());
        return resolved;
    }

    private List<RegisteredAdministratorEndpoint> collectRegisteredAdministratorEndpoints() {
        List<RegisteredAdministratorEndpoint> collected = new ArrayList<>();
        handlerMappings.forEach(handlerMapping -> handlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {
            if (!isAdministratorEndpoint(handlerMethod)) {
                return;
            }
            var patternsCondition = mappingInfo.getPathPatternsCondition();
            if (patternsCondition != null) {
                Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
                patternsCondition.getPatterns().forEach(pattern -> collected.add(new RegisteredAdministratorEndpoint(pattern, methods)));
            }
        }));
        return List.copyOf(collected);
    }

    private static boolean isAdministratorEndpoint(HandlerMethod handlerMethod) {
        return isAnnotated(handlerMethod, EnforceAdmin.class) || isAnnotated(handlerMethod, EnforceSuperAdmin.class);
    }

    private static boolean isAnnotated(HandlerMethod handlerMethod, Class<? extends java.lang.annotation.Annotation> annotation) {
        // Both placements count: several resources carry the annotation on the class rather than on every method.
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), annotation) || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), annotation);
    }

    /**
     * One registered administrator endpoint: the path it answers and the HTTP methods it answers it for.
     *
     * @param pattern the registered path pattern
     * @param methods the mapped HTTP methods, empty when the mapping declares none and therefore answers all of them
     */
    private record RegisteredAdministratorEndpoint(PathPattern pattern, Set<RequestMethod> methods) {

        boolean matches(PathContainer path, String requestMethod) {
            return pattern.matches(path) && matchesMethod(requestMethod);
        }

        private boolean matchesMethod(String requestMethod) {
            if (methods.isEmpty()) {
                return true;
            }
            // HEAD is answered by the GET handler, so the same administrator endpoint serves it.
            String effectiveMethod = HttpMethod.HEAD.matches(requestMethod) ? HttpMethod.GET.name() : requestMethod;
            return methods.stream().anyMatch(method -> method.name().equals(effectiveMethod));
        }
    }
}
