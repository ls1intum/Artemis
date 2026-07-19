package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

class WebsocketSecurityConfigurationTest {

    private final WebsocketSecurityConfiguration configuration = new WebsocketSecurityConfiguration();

    @Test
    void shouldProtectResolvedHyperionAndBrokerRegistrySubscriptions() {
        var manager = configuration.authorizationManager(MessageMatcherDelegatingAuthorizationManager.builder());
        var authentication = UsernamePasswordAuthenticationToken.authenticated("user", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(manager.authorize(() -> authentication, subscription("/user/topic/hyperion/exercise-generation/jobs/job-1")).isGranted()).isTrue();
        assertThat(manager.authorize(() -> authentication, subscription("/topic/hyperion/exercise-generation/exercises/42/state")).isGranted()).isTrue();
        assertThat(manager.authorize(() -> authentication, subscription("/topic/hyperion/exercise-generation/jobs/job-1-user-victim")).isGranted()).isFalse();
        assertThat(manager.authorize(() -> authentication, subscription("/topic/user-registry")).isGranted()).isFalse();
        assertThat(manager.authorize(() -> authentication, subscription("/topic/unresolved-user")).isGranted()).isFalse();
        assertThat(manager.authorize(() -> authentication, subscription("/topic/exercises/42/synchronization")).isGranted()).isTrue();
    }

    private Message<byte[]> subscription(String destination) {
        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
