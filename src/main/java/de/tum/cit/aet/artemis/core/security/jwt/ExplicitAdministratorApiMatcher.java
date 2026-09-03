package de.tum.cit.aet.artemis.core.security.jwt;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.PathContainer;
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
 * ordinary neighbours as well. Matching the registered patterns has neither problem, and an administrator endpoint
 * added later is covered without anyone remembering this class exists.
 */
public class ExplicitAdministratorApiMatcher {

    private static final Logger log = LoggerFactory.getLogger(ExplicitAdministratorApiMatcher.class);

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    /**
     * Resolved on first use rather than in the constructor: this collaborator is built while the security filter chain
     * is, which is before the handler mappings exist. Written once and read without synchronisation afterwards, so a
     * race can only cost a second scan.
     */
    private volatile List<PathPattern> administratorPatterns;

    public ExplicitAdministratorApiMatcher(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    /**
     * @param request the request being filtered
     * @return whether an administrator endpoint serves it, and its administrator authorities therefore have to survive
     *         the filter
     */
    public boolean matches(HttpServletRequest request) {
        List<PathPattern> patterns = administratorPatterns();
        if (patterns.isEmpty()) {
            return false;
        }
        PathContainer path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private List<PathPattern> administratorPatterns() {
        List<PathPattern> resolved = administratorPatterns;
        if (resolved != null) {
            return resolved;
        }
        resolved = collectAdministratorPatterns();
        if (resolved.isEmpty()) {
            // Nothing to cache yet: either the handler mappings are not built, or this node serves no web endpoints at
            // all. Retrying costs a scan on the next request and avoids caching an answer that is only true for now.
            log.debug("No administrator endpoint mappings found yet, not caching the result");
            return List.of();
        }
        administratorPatterns = resolved;
        log.debug("Found {} administrator endpoint patterns", resolved.size());
        return resolved;
    }

    private List<PathPattern> collectAdministratorPatterns() {
        List<PathPattern> collected = new ArrayList<>();
        handlerMappings.forEach(handlerMapping -> handlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {
            if (!isAdministratorEndpoint(handlerMethod)) {
                return;
            }
            var patternsCondition = mappingInfo.getPathPatternsCondition();
            if (patternsCondition != null) {
                collected.addAll(patternsCondition.getPatterns());
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
}
