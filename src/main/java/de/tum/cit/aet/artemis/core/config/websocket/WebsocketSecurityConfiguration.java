package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.ElevatedAccessService;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

@Profile(PROFILE_CORE)
@Configuration
@EnableWebSocketSecurity
@Lazy
public class WebsocketSecurityConfiguration {

    @Bean
    AuthorizationManager<Message<?>> authorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages, UserRepository userRepository,
            CourseRepository courseRepository, ObjectProvider<ElevatedAccessService> elevatedAccessService) {
        var instructor = new CourseTopicAuthorizationManager(userRepository, courseRepository, elevatedAccessService, CourseRole.INSTRUCTOR);
        var editor = new CourseTopicAuthorizationManager(userRepository, courseRepository, elevatedAccessService, CourseRole.EDITOR);
        var student = new CourseTopicAuthorizationManager(userRepository, courseRepository, elevatedAccessService, CourseRole.STUDENT);
        // @formatter:off
        messages
            .matchers(WebsocketSecurityConfiguration::isUnsafeClientDestination).denyAll()
            .nullDestMatcher().authenticated()
            // These destinations belong to the relay, never to a connected client.
            .simpDestMatchers("/topic/unresolved-user", "/topic/unresolved-user/**", "/topic/user-registry", "/topic/user-registry/**").denyAll()
            // Clients must not impersonate publishers of server-owned course or personal feeds.
            .simpMessageDestMatchers("/topic/atlas/orchestrator", "/topic/atlas/orchestrator/**",
                "/topic/notification", "/topic/notification/**", "/topic/communication/notification", "/topic/communication/notification/**",
                "/topic/system-notification", "/topic/iris", "/topic/iris/**", "/topic/courses", "/topic/courses/**",
                "/user/topic/notification", "/user/topic/notification/**", "/user/topic/communication/notification", "/user/topic/communication/notification/**",
                "/user/topic/system-notification", "/user/topic/iris", "/user/topic/iris/**").denyAll()
            .simpDestMatchers("/topic").hasAuthority(Role.ADMIN.getAuthority())
            .simpSubscribeDestMatchers("/topic/atlas/orchestrator/{courseId}").access(instructor)
            .simpDestMatchers("/topic/atlas/orchestrator", "/topic/atlas/orchestrator/**").denyAll()
            .simpSubscribeDestMatchers("/topic/courses/{courseId}/operation-progress", "/topic/courses/{courseId}/export-course",
                "/topic/courses/{courseId}/queued-jobs", "/topic/courses/{courseId}/running-jobs", "/topic/courses/{courseId}/finished-jobs",
                "/topic/courses/{courseId}/build-job/{jobId}").access(instructor)
            .simpSubscribeDestMatchers("/topic/courses/{courseId}/**").access(student)
            .simpDestMatchers("/topic/courses", "/topic/courses/**").denyAll()
            .simpSubscribeDestMatchers("/topic/notification/system-notification").authenticated()
            // Only logical /user destinations may reach personal notifications, never resolved broker destinations.
            .simpDestMatchers("/topic/notification", "/topic/notification/**", "/topic/communication/notification", "/topic/communication/notification/**").denyAll()
            .simpSubscribeDestMatchers("/user/topic/notification/all").authenticated()
            .simpSubscribeDestMatchers("/user/topic/notification/{courseId}").access(student)
            .simpDestMatchers("/user/topic/notification", "/user/topic/notification/**").denyAll()
            .simpSubscribeDestMatchers("/user/topic/iris/competencies/{courseId}").access(editor)
            .simpDestMatchers("/user/topic/iris/competencies", "/user/topic/iris/competencies/**").denyAll()
            .simpSubscribeDestMatchers("/user/topic/iris/**").authenticated()
            .simpDestMatchers("/topic/iris", "/topic/iris/**").denyAll()
            // Preserve unrelated application-handled topics and their existing resource-specific interceptors.
            .simpDestMatchers("/topic/**").authenticated()
            // message types other than MESSAGE and SUBSCRIBE
            .simpTypeMatchers(SimpMessageType.MESSAGE, SimpMessageType.SUBSCRIBE).denyAll()
            // catch all
            .anyMessage().denyAll();
        return messages.build();
        // @formatter:on
    }

    /**
     * Only literal client destinations are safe: broker patterns can bypass course-specific matchers.
     * Control frames without destinations still follow the authenticated null-destination rule.
     */
    private static boolean isUnsafeClientDestination(Message<?> message) {
        SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
        if (type != SimpMessageType.MESSAGE && type != SimpMessageType.SUBSCRIBE) {
            return false;
        }
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination == null || destination.isBlank()) {
            return true;
        }
        for (int i = 0; i < destination.length(); i++) {
            switch (destination.charAt(i)) {
                case '*', '#', '?', '{', '}', '>', ',' -> {
                    return true;
                }
                default -> {
                    // A literal destination character.
                }
            }
        }
        return false;
    }
}
