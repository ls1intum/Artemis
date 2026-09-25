package de.tum.cit.aet.artemis.core.config.websocket;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.service.ElevatedAccessService;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

/**
 * Authorizes a literal course topic using the matcher's {@code courseId} variable.
 * Administrator elevation belongs to the supplied WebSocket session, not the channel thread.
 * Invalid principals, missing courses and failed lookups deny access without falling through to broader rules.
 */
public class CourseTopicAuthorizationManager implements AuthorizationManager<MessageAuthorizationContext<?>> {

    private static final Logger log = LoggerFactory.getLogger(CourseTopicAuthorizationManager.class);

    private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);

    private static final AuthorizationDecision DENIED = new AuthorizationDecision(false);

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ObjectProvider<ElevatedAccessService> elevatedAccessService;

    private final List<CourseRole> minimumRoles;

    public CourseTopicAuthorizationManager(UserRepository userRepository, CourseRepository courseRepository, ObjectProvider<ElevatedAccessService> elevatedAccessService,
            CourseRole minimumRole) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.elevatedAccessService = elevatedAccessService;
        this.minimumRoles = CourseRole.valuesAtLeast(minimumRole);
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authenticationSupplier, MessageAuthorizationContext<?> context) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken || authentication.getName() == null
                || authentication.getName().isBlank()) {
            return DENIED;
        }

        final long courseId;
        try {
            courseId = Long.parseLong(context.getVariables().get("courseId"));
        }
        catch (NumberFormatException exception) {
            return DENIED;
        }
        if (courseId <= 0) {
            return DENIED;
        }

        try {
            boolean permitted = userRepository.existsByLoginInCourseWithMinRole(authentication.getName(), courseId, minimumRoles)
                    || elevatedAccessService.getObject().isAdminElevationActive(authentication);
            return permitted && courseRepository.existsById(courseId) ? GRANTED : DENIED;
        }
        catch (RuntimeException exception) {
            log.warn("Could not authorize WebSocket subscription for user {} in course {}", authentication.getName(), courseId, exception);
            return DENIED;
        }
    }
}
