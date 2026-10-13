package de.tum.cit.aet.artemis.iris.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * Determines whether a user currently has a specific Iris chat session open anywhere (any device or
 * browser tab) by checking for an active websocket subscription to the session topic.
 * <p>
 * Used to decide whether an Iris answer needs to be delivered as a notification: only when the chat
 * is not open live somewhere. The injected {@link SimpUserRegistry} is the cluster-wide aggregate
 * (Artemis enables {@code setUserRegistryBroadcast} on the broker relay), so this reflects
 * subscriptions across all server nodes, not just the local one.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisSessionPresenceService {

    /**
     * Suffix of the destination a client subscribes to for a session. The web and mobile clients
     * subscribe to the user destination {@code /user/topic/iris/{sessionId}}, which is why we match
     * on the suffix rather than the full destination. The leading slash before the id enforces a
     * boundary so e.g. session 12 does not match session 112.
     */
    private static final String SESSION_TOPIC_PREFIX = "/topic/iris/";

    private final SimpUserRegistry userRegistry;

    public IrisSessionPresenceService(SimpUserRegistry userRegistry) {
        this.userRegistry = userRegistry;
    }

    /**
     * Checks whether the given user is subscribed to the websocket topic of the given Iris session
     * on any of their active websocket sessions.
     *
     * @param userLogin the login of the user
     * @param sessionId the id of the Iris session
     * @return true if the session topic is subscribed somewhere, false otherwise
     */
    public boolean isSessionOpenAnywhere(String userLogin, long sessionId) {
        var user = userRegistry.getUser(userLogin);
        if (user == null) {
            return false;
        }
        String topicSuffix = SESSION_TOPIC_PREFIX + sessionId;
        return user.getSessions().stream().flatMap(session -> session.getSubscriptions().stream())
                .anyMatch(subscription -> subscription.getDestination() != null && subscription.getDestination().endsWith(topicSuffix));
    }
}
