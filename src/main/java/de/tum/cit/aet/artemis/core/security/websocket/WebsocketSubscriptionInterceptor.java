package de.tum.cit.aet.artemis.core.security.websocket;

import java.security.Principal;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

/**
 * Applies the {@link WebsocketTopicRegistry} to every SUBSCRIBE frame a client sends. A rejected subscription is dropped silently, so the client keeps its connection and
 * its other subscriptions; it simply never receives a message on that destination.
 * <p>
 * This interceptor only enforces the declarations. Who may subscribe to a topic is declared with the topic itself, see {@link WebsocketTopic}.
 */
public class WebsocketSubscriptionInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebsocketSubscriptionInterceptor.class);

    private final Supplier<WebsocketTopicRegistry> registry;

    /**
     * @param registry supplies the registry on the first subscription, so that creating the channel does not pull the registry into application startup
     */
    public WebsocketSubscriptionInterceptor(Supplier<WebsocketTopicRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.SUBSCRIBE.equals(headerAccessor.getCommand())) {
            return message;
        }
        Principal subscriber = headerAccessor.getUser();
        String destination = headerAccessor.getDestination();
        WebsocketTopicRegistry.Decision decision;
        try {
            decision = registry.get().authorizeSubscription(subscriber, destination);
        }
        catch (RuntimeException e) {
            // Fail closed, but keep the connection and the other subscriptions of the client
            log.error("Could not check the subscription to {}, rejecting it", destination, e);
            return null;
        }
        if (decision == WebsocketTopicRegistry.Decision.ALLOWED) {
            return message;
        }
        String login = subscriber != null ? subscriber.getName() : "anonymous";
        switch (decision) {
            case UNDECLARED -> log.warn("Rejected the subscription of {} to {}: no websocket topic declares this destination", login, destination);
            case DENIED -> log.warn("Rejected the subscription of {} to {}: the access rule of the websocket topic does not admit the user", login, destination);
            default -> log.warn("Rejected the subscription of {} to {}: invalid subscription", login, destination);
        }
        // Returning null drops the SUBSCRIBE frame.
        return null;
    }
}
