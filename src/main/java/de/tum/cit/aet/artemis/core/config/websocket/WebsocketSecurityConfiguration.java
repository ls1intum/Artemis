package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;

import de.tum.cit.aet.artemis.core.security.Role;

@Profile(PROFILE_CORE)
@Configuration
// NOTE: due to an issue in Spring Security, we had to use the old "deprecated" way with extending AbstractSecurityWebSocketMessageBrokerConfigurer
// https://github.com/spring-projects/spring-security/issues/16299
// As soon as this issue was addressed in a future Spring Framework / Spring Security, we can switch to the new way by using @EnableWebSocketSecurity again
// @EnableWebSocketSecurity
public class WebsocketSecurityConfiguration extends AbstractSecurityWebSocketMessageBrokerConfigurer {

    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        // @formatter:off
        messages
            .nullDestMatcher()
            .authenticated()
            // matches any destination that starts with /topic/
            // (i.e. cannot send messages directly to /topic/)
            // (i.e. cannot subscribe to /topic/messages/* to get messages sent to
            // /topic/messages-user<id>)
            .simpDestMatchers("/topic")
                .hasAuthority(Role.ADMIN.getAuthority())
            .simpDestMatchers("/topic/**")
                .authenticated()
            // message types other than MESSAGE and SUBSCRIBE
            .simpTypeMatchers(SimpMessageType.MESSAGE, SimpMessageType.SUBSCRIBE).denyAll()
            // catch all
                .anyMessage()
                .denyAll();
        // @formatter:on
    }

}
