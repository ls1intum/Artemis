package de.tum.cit.aet.artemis.core.security.websocket;

/**
 * A concrete destination of a declared {@link WebsocketUserTopic}, created with {@link WebsocketUserTopic#at(Object...)}.
 *
 * @param topic the declared topic
 * @param value the destination without the {@code /user} prefix, e.g. {@code /topic/newResults}
 */
public record WebsocketUserDestination(WebsocketUserTopic topic, String value) {

    /**
     * @throws IllegalArgumentException if the value is not a destination of the topic
     */
    public WebsocketUserDestination {
        if (topic == null || topic.match(value).isEmpty()) {
            throw new IllegalArgumentException(value + " is not a destination of the websocket topic " + topic);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
