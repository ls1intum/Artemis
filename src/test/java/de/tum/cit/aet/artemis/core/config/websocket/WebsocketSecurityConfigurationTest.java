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

import de.tum.cit.aet.artemis.core.security.Role;

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

    @Test
    void nonAdminIsDeniedOnTheAdminOnlyTopicMatcher() {
        var manager = configuration.authorizationManager(MessageMatcherDelegatingAuthorizationManager.builder());
        var authentication = UsernamePasswordAuthenticationToken.authenticated("user", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(manager.authorize(() -> authentication, subscription("/topic")).isGranted()).isFalse();
    }

    @Test
    void adminIsGrantedOnTheAdminOnlyTopicMatcher() {
        var manager = configuration.authorizationManager(MessageMatcherDelegatingAuthorizationManager.builder());
        var authentication = UsernamePasswordAuthenticationToken.authenticated("admin", "password", List.of(new SimpleGrantedAuthority(Role.ADMIN.getAuthority())));

        assertThat(manager.authorize(() -> authentication, subscription("/topic")).isGranted()).isTrue();
    }

    @Test
    void unmatchedDestinationHitsTheCatchAllDenyAll() {
        var manager = configuration.authorizationManager(MessageMatcherDelegatingAuthorizationManager.builder());
        var authentication = UsernamePasswordAuthenticationToken.authenticated("user", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // A DISCONNECT frame is neither MESSAGE nor SUBSCRIBE (so simpTypeMatchers(...).denyAll() does not apply), and its destination matches none of the /topic
        // matchers, so this must fall through to the anyMessage().denyAll() catch-all.
        assertThat(manager.authorize(() -> authentication, message(StompCommand.DISCONNECT, "/queue/unmatched")).isGranted()).isFalse();
    }

    private Message<byte[]> subscription(String destination) {
        return message(StompCommand.SUBSCRIBE, destination);
    }

    private Message<byte[]> message(StompCommand command, String destination) {
        var accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
