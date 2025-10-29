package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@EnforceAdmin
@RequestMapping("api/core/admin/metrics/")
public class AdminMetricsResource {

    private static final Logger log = LoggerFactory.getLogger(AdminMetricsResource.class);

    private final SimpUserRegistry userRegistry;

    public AdminMetricsResource(SimpUserRegistry userRegistry) {
        this.userRegistry = userRegistry;
    }

    /**
     * Retrieves the active WebSocket subscriptions for all users in the system.
     *
     * <p>
     * This endpoint provides a structured overview of WebSocket users,
     * their sessions, and their subscribed destinations. It transforms
     * the internal representation of active WebSocket connections into
     * a format suitable for clients.
     * </p>
     *
     * <p>
     * Only users with at least student-level access in a course are
     * authorized to retrieve this information.
     * </p>
     *
     * @return A {@link ResponseEntity} containing a set of {@link WebsocketUserDTO} objects,
     *         where each object represents a user along with their active WebSocket sessions
     *         and subscriptions.
     */
    @GetMapping("websocket-subscriptions")
    public ResponseEntity<Set<WebsocketUserDTO>> getCourseMetricsForUser() {
        log.debug("REST request to get websocket-subscriptions");
        // Convert simp data structure into user -> session -> subscriptions
        Set<WebsocketUserDTO> users = userRegistry.getUsers().stream().map(user -> new WebsocketUserDTO(user.getName(), user.getSessions().stream()
                .map(session -> new WebsocketSessionDTO(session.getId(), session.getSubscriptions().stream().map(SimpSubscription::getDestination).collect(Collectors.toSet())))
                .collect(Collectors.toSet()))).collect(Collectors.toSet());
        return ResponseEntity.ok(users);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record WebsocketUserDTO(String username, Set<WebsocketSessionDTO> sessions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record WebsocketSessionDTO(String sessionId, Set<String> subscribedTopics) {
    }
}
