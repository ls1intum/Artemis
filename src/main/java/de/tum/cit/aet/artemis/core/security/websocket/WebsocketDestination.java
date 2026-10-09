package de.tum.cit.aet.artemis.core.security.websocket;

/**
 * A concrete destination of a declared {@link WebsocketTopic}, created with {@link WebsocketTopic#at(Object...)}.
 *
 * @param topic the declared topic
 * @param value the destination, e.g. {@code /topic/exercise/42/newResults}
 */
public record WebsocketDestination(WebsocketTopic topic, String value) {

    /**
     * @throws IllegalArgumentException if the value is not a destination of the topic
     */
    public WebsocketDestination {
        if (topic == null || topic.match(value).isEmpty()) {
            throw new IllegalArgumentException(value + " is not a destination of the websocket topic " + topic);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
