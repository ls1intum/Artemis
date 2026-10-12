package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

/**
 * Unit tests for {@link IrisSessionPresenceService}. Verifies the websocket-subscription matching used to decide
 * whether an Iris chat session is open anywhere, using a mocked {@link SimpUserRegistry}.
 */
class IrisSessionPresenceServiceTest {

    private static final String USER_LOGIN = "student1";

    private SimpUserRegistry userRegistry;

    private IrisSessionPresenceService presenceService;

    @BeforeEach
    void setUp() {
        userRegistry = mock(SimpUserRegistry.class);
        presenceService = new IrisSessionPresenceService(userRegistry);
    }

    @Test
    void isSessionOpenAnywhere_returnsFalseWhenUserNotConnected() {
        when(userRegistry.getUser(USER_LOGIN)).thenReturn(null);

        assertThat(presenceService.isSessionOpenAnywhere(USER_LOGIN, 12L)).isFalse();
    }

    @Test
    void isSessionOpenAnywhere_returnsTrueWhenSubscribedToSessionTopic() {
        mockUserWithSubscriptions("/user/topic/iris/12");

        assertThat(presenceService.isSessionOpenAnywhere(USER_LOGIN, 12L)).isTrue();
    }

    @Test
    void isSessionOpenAnywhere_returnsFalseWhenNoSubscriptions() {
        mockUserWithSubscriptions();

        assertThat(presenceService.isSessionOpenAnywhere(USER_LOGIN, 12L)).isFalse();
    }

    @Test
    void isSessionOpenAnywhere_returnsFalseWhenSubscribedToDifferentSession() {
        mockUserWithSubscriptions("/user/topic/iris/99");

        assertThat(presenceService.isSessionOpenAnywhere(USER_LOGIN, 12L)).isFalse();
    }

    /**
     * The leading slash in the topic suffix ({@code /topic/iris/12}) is what prevents session 12 from matching the
     * subscription of session 112. This boundary is the whole reason for the slash, so it gets an explicit test.
     */
    @Test
    void isSessionOpenAnywhere_doesNotMatchSessionIdPrefix() {
        mockUserWithSubscriptions("/user/topic/iris/112");

        assertThat(presenceService.isSessionOpenAnywhere(USER_LOGIN, 12L)).isFalse();
    }

    @Test
    void isSessionOpenAnywhere_returnsTrueWhenOneOfManySubscriptionsMatches() {
        mockUserWithSubscriptions("/user/topic/iris/7", "/user/topic/iris/12", "/user/topic/something-else");

        assertThat(presenceService.isSessionOpenAnywhere(USER_LOGIN, 12L)).isTrue();
    }

    @Test
    void isSessionOpenAnywhere_ignoresSubscriptionsWithNullDestination() {
        SimpSubscription nullDestinationSubscription = mock(SimpSubscription.class);
        when(nullDestinationSubscription.getDestination()).thenReturn(null);
        SimpSession session = mock(SimpSession.class);
        when(session.getSubscriptions()).thenReturn(Set.of(nullDestinationSubscription));
        SimpUser user = mock(SimpUser.class);
        when(user.getSessions()).thenReturn(Set.of(session));
        when(userRegistry.getUser(USER_LOGIN)).thenReturn(user);

        assertThat(presenceService.isSessionOpenAnywhere(USER_LOGIN, 12L)).isFalse();
    }

    private void mockUserWithSubscriptions(String... destinations) {
        Set<SimpSubscription> subscriptions = Arrays.stream(destinations).map(destination -> {
            SimpSubscription subscription = mock(SimpSubscription.class);
            when(subscription.getDestination()).thenReturn(destination);
            return subscription;
        }).collect(Collectors.toSet());

        SimpSession session = mock(SimpSession.class);
        when(session.getSubscriptions()).thenReturn(subscriptions);
        SimpUser user = mock(SimpUser.class);
        when(user.getSessions()).thenReturn(Set.of(session));
        when(userRegistry.getUser(USER_LOGIN)).thenReturn(user);
    }
}
