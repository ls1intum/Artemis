package de.tum.cit.aet.artemis.core.security.websocket;

import java.util.Map;
import java.util.Optional;

/**
 * A per-user topic: the server addresses each message to one login with {@code websocketMessagingService.sendMessageToUser(login, TOPIC.at(...), payload)}, and Spring
 * delivers it only to the sessions of that user. Clients subscribe to it with the {@code /user} prefix, e.g. {@code /user/topic/newResults}.
 * <p>
 * Because delivery is per user, the topic needs no access rule; the server decides who receives each message. Declare it as a {@code public static final} constant of the
 * {@link WebsocketTopicProvider} of the module that owns it, so that subscriptions to it are accepted.
 */
public final class WebsocketUserTopic {

    private final WebsocketTopicTemplate template;

    private WebsocketUserTopic(WebsocketTopicTemplate template) {
        this.template = template;
    }

    /**
     * Declares a per-user topic.
     *
     * @param template the destination template without the {@code /user} prefix, e.g. {@code /topic/newResults}
     * @return the topic
     * @throws IllegalArgumentException if the template is malformed
     */
    public static WebsocketUserTopic of(String template) {
        return new WebsocketUserTopic(WebsocketTopicTemplate.parse(template));
    }

    /**
     * Resolves the destination for the given variable values.
     *
     * @param values one value per template variable, in the order they appear in the template
     * @return the destination
     */
    public WebsocketUserDestination at(Object... values) {
        return new WebsocketUserDestination(this, template.expand(values));
    }

    /**
     * @return the destination template without the {@code /user} prefix
     */
    public String template() {
        return template.template();
    }

    /**
     * Matches a concrete destination against this topic.
     *
     * @param destination the destination, e.g. {@code /topic/participations/42/team}
     * @return the values of the template variables by name if the destination belongs to this topic, empty otherwise
     */
    public Optional<Map<String, String>> match(String destination) {
        return template.match(destination);
    }

    int literalCount() {
        return template.literalCount();
    }

    /**
     * @return a destination of this topic with the value {@code 1} for every variable, used to check that the declared topics do not overlap
     */
    String sampleDestination() {
        return template.expand(template.variableNames().stream().map(_ -> "1").toArray());
    }

    @Override
    public String toString() {
        return "/user" + template.template();
    }
}
