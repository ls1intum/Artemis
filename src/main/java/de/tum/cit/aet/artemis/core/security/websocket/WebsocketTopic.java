package de.tum.cit.aet.artemis.core.security.websocket;

import java.util.Map;
import java.util.Optional;

/**
 * A broadcast topic: every client subscribed to a destination of this topic receives the messages sent to it, so the topic declares who may subscribe.
 * <p>
 * Declare each topic as a {@code public static final} constant of the {@link WebsocketTopicProvider} of the module that owns it, and send with
 * {@code websocketMessagingService.sendMessage(TOPIC.at(id), payload)}:
 *
 * <pre>
 *
 * public static final WebsocketTopic COURSE_WIDE_POSTS = WebsocketTopic.of("/topic/communication/courses/{courseId}", WebsocketTopicAccess.atLeastStudentInCourse("courseId"));
 * </pre>
 *
 * A payload meant for a single user belongs on a {@link WebsocketUserTopic} instead, which needs no access rule.
 */
public final class WebsocketTopic {

    private final WebsocketTopicTemplate template;

    private final WebsocketTopicAccess access;

    private final boolean compressed;

    private WebsocketTopic(WebsocketTopicTemplate template, WebsocketTopicAccess access, boolean compressed) {
        this.template = template;
        this.access = access;
        this.compressed = compressed;
    }

    /**
     * Declares a broadcast topic.
     *
     * @param template the destination template, starting with {@code /topic/}, with one {@code {variable}} per id segment
     * @param access   who may subscribe
     * @return the topic
     * @throws IllegalArgumentException if the template is malformed or the access rule reads a variable the template does not declare
     */
    public static WebsocketTopic of(String template, WebsocketTopicAccess access) {
        if (access == null) {
            throw new IllegalArgumentException("The websocket topic " + template + " needs an access rule");
        }
        var parsedTemplate = WebsocketTopicTemplate.parse(template);
        for (String variable : access.variableNames()) {
            if (!parsedTemplate.variableNames().contains(variable)) {
                throw new IllegalArgumentException("The access rule " + access + " of the websocket topic " + template + " reads the undeclared variable {" + variable + "}");
            }
        }
        return new WebsocketTopic(parsedTemplate, access, false);
    }

    /**
     * Returns a copy whose messages are sent gzip-compressed. Only worth it for large, frequent payloads such as the build queue.
     *
     * @return the compressed topic
     */
    public WebsocketTopic withCompression() {
        return new WebsocketTopic(template, access, true);
    }

    /**
     * Resolves the destination for the given variable values.
     *
     * @param values one value per template variable, in the order they appear in the template
     * @return the destination
     */
    public WebsocketDestination at(Object... values) {
        return new WebsocketDestination(this, template.expand(values));
    }

    /**
     * @return the destination template
     */
    public String template() {
        return template.template();
    }

    /**
     * @return who may subscribe
     */
    public WebsocketTopicAccess access() {
        return access;
    }

    /**
     * @return whether messages are sent gzip-compressed
     */
    public boolean isCompressed() {
        return compressed;
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
     * @param other another topic
     * @return a destination that both topics match, used to check that the declared topics do not overlap; empty if there is none
     */
    Optional<String> commonDestination(WebsocketTopic other) {
        return template.commonDestination(other.template);
    }

    @Override
    public String toString() {
        return template.template() + " " + access;
    }
}
