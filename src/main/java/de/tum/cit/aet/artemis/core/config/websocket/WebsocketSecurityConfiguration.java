package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.websocket.WebsocketConfiguration.APPLICATION_DESTINATION_PREFIX;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/**
 * The frame-level rules of the websocket connection. Spring Security applies them to every frame a client sends, before {@code WebsocketSubscriptionInterceptor} decides
 * which subscriptions are allowed based on the declared websocket topics (see {@code WebsocketTopic}).
 * <p>
 * A SEND or SUBSCRIBE frame that breaks these rules is dropped (see {@code WebsocketConfiguration}); an unauthenticated CONNECT is refused. A legitimate client never
 * sends such a frame.
 */
@Profile(PROFILE_CORE)
@Configuration
@EnableWebSocketSecurity
@Lazy
public class WebsocketSecurityConfiguration {

    @Bean
    AuthorizationManager<Message<?>> authorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        // @formatter:off
        messages
            // CONNECT, UNSUBSCRIBE, DISCONNECT and heartbeats carry no destination
            .nullDestMatcher().authenticated()
            // Broadcast topics and per-user topics. Which of them exist and who may subscribe to each is declared with the topic itself and checked by WebsocketSubscriptionInterceptor.
            .simpSubscribeDestMatchers("/topic/**", "/user/topic/**").authenticated()
            // Client messages only reach the @MessageMapping handlers, which check the permissions of the sender themselves
            .simpMessageDestMatchers(APPLICATION_DESTINATION_PREFIX + "/**").authenticated()
            // Everything else, in particular messages sent directly to a broker destination
            .anyMessage().denyAll();
        return messages.build();
        // @formatter:on
    }

    /**
     * Replaces Spring Security's CSRF check of the STOMP CONNECT frame, which expects a token from an HTTP session that Artemis does not use. The websocket handshake is
     * authenticated with the JWT, whose cookie is {@code SameSite=Lax}, so a cross-site page cannot open an authenticated connection.
     *
     * @return an interceptor that lets every frame pass
     */
    @Bean(name = "csrfChannelInterceptor")
    ChannelInterceptor csrfChannelInterceptor() {
        return new ChannelInterceptor() {
        };
    }
}
