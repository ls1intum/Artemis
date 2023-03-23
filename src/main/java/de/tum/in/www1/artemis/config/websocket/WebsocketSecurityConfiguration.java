package de.tum.in.www1.artemis.config.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;

import de.tum.in.www1.artemis.security.Role;

@Configuration
public class WebsocketSecurityConfiguration extends AbstractSecurityWebSocketMessageBrokerConfigurer {

    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        messages.nullDestMatcher().authenticated().simpDestMatchers("/topic").hasAuthority(Role.ADMIN.getAuthority())
                // matches any destination that starts with /topic/
                // (i.e. cannot send messages directly to /topic/)
                // (i.e. cannot subscribe to /topic/messages/* to get messages sent to
                // /topic/messages-user<id>)
                .simpDestMatchers("/topic/**").authenticated()
                // message types other than MESSAGE and SUBSCRIBE
                .simpTypeMatchers(SimpMessageType.MESSAGE, SimpMessageType.SUBSCRIBE).denyAll()
                // catch all
                .anyMessage().denyAll();
    }
}
