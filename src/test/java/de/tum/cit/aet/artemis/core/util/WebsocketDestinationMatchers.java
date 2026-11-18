package de.tum.cit.aet.artemis.core.util;

import static org.mockito.ArgumentMatchers.argThat;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserDestination;

/**
 * Mockito matchers for the destinations passed to {@code WebsocketMessagingService}. They compare the destination as it goes over the wire, so a test fails when the
 * destination changes, since clients subscribe to it by that exact string.
 */
public final class WebsocketDestinationMatchers {

    private WebsocketDestinationMatchers() {
    }

    /**
     * @param destination the expected destination, e.g. {@code /topic/exercise/42/newResults}
     * @return a matcher for a broadcast destination with that value
     */
    public static WebsocketDestination topic(String destination) {
        return argThat((WebsocketDestination actual) -> actual != null && destination.equals(actual.value()));
    }

    /**
     * @param regex a regular expression for the expected destination
     * @return a matcher for a broadcast destination whose value matches the expression
     */
    public static WebsocketDestination topicMatching(String regex) {
        return argThat((WebsocketDestination actual) -> actual != null && actual.value().matches(regex));
    }

    /**
     * @param destination the expected destination without the {@code /user} prefix, e.g. {@code /topic/newResults}
     * @return a matcher for a per-user destination with that value
     */
    public static WebsocketUserDestination userTopic(String destination) {
        return argThat((WebsocketUserDestination actual) -> actual != null && destination.equals(actual.value()));
    }
}
