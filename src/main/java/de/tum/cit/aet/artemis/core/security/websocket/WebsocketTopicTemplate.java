package de.tum.cit.aet.artemis.core.security.websocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The parsed form of a topic template such as {@code /topic/courses/{courseId}/quizExercises}: literal segments and named variables, each variable standing for exactly one
 * whole segment. Matching is an exact segment-by-segment comparison, so a destination can never reach a topic through a prefix, a pattern or an encoding trick.
 */
final class WebsocketTopicTemplate {

    static final String TOPIC_PREFIX = "/topic/";

    private static final Pattern VARIABLE_SEGMENT = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)}");

    private static final Pattern LITERAL_SEGMENT = Pattern.compile("[a-zA-Z0-9._-]+");

    private final String template;

    /**
     * The segments after splitting at {@code /}; a variable segment holds {@code null}.
     */
    private final List<String> literals;

    /**
     * The variable name of each segment; a literal segment holds {@code null}.
     */
    private final List<String> variables;

    private final List<String> variableNames;

    private final int literalCount;

    private WebsocketTopicTemplate(String template, List<String> literals, List<String> variables) {
        this.template = template;
        this.literals = literals;
        this.variables = variables;
        this.variableNames = variables.stream().filter(name -> name != null).toList();
        this.literalCount = (int) literals.stream().filter(literal -> literal != null).count();
    }

    /**
     * Parses and validates a template. A template starts with {@code /topic/}, has no empty segments, and every variable is a whole segment with a unique name.
     *
     * @param template the template, e.g. {@code /topic/exercise/{exerciseId}/newResults}
     * @return the parsed template
     * @throws IllegalArgumentException if the template is malformed
     */
    static WebsocketTopicTemplate parse(String template) {
        if (template == null || !template.startsWith(TOPIC_PREFIX) || template.endsWith("/")) {
            throw new IllegalArgumentException("A websocket topic template must start with " + TOPIC_PREFIX + " and must not end with /: " + template);
        }
        String[] segments = template.substring(1).split("/", -1);
        List<String> literals = new ArrayList<>(segments.length);
        List<String> variables = new ArrayList<>(segments.length);
        Set<String> seenVariables = new HashSet<>();
        for (String segment : segments) {
            var variableMatcher = VARIABLE_SEGMENT.matcher(segment);
            if (variableMatcher.matches()) {
                String name = variableMatcher.group(1);
                if (!seenVariables.add(name)) {
                    throw new IllegalArgumentException("The websocket topic template " + template + " declares the variable {" + name + "} twice");
                }
                literals.add(null);
                variables.add(name);
            }
            else if (LITERAL_SEGMENT.matcher(segment).matches()) {
                literals.add(segment);
                variables.add(null);
            }
            else {
                throw new IllegalArgumentException("The websocket topic template " + template + " has the invalid segment '" + segment + "'");
            }
        }
        return new WebsocketTopicTemplate(template, Collections.unmodifiableList(literals), Collections.unmodifiableList(variables));
    }

    String template() {
        return template;
    }

    List<String> variableNames() {
        return variableNames;
    }

    /**
     * The number of literal segments, used to prefer {@code /topic/things/special} over {@code /topic/things/{thingId}} when both match.
     *
     * @return the number of literal segments
     */
    int literalCount() {
        return literalCount;
    }

    /**
     * Finds a destination that both templates match: they have the same number of segments, and at every segment both hold the same literal or at least one holds a
     * variable.
     *
     * @param other the other template
     * @return such a destination, or empty if no destination matches both templates
     */
    Optional<String> commonDestination(WebsocketTopicTemplate other) {
        if (literals.size() != other.literals.size()) {
            return Optional.empty();
        }
        StringBuilder destination = new StringBuilder();
        for (int i = 0; i < literals.size(); i++) {
            String literal = literals.get(i);
            String otherLiteral = other.literals.get(i);
            if (literal != null && otherLiteral != null && !literal.equals(otherLiteral)) {
                return Optional.empty();
            }
            destination.append('/').append(literal != null ? literal : otherLiteral != null ? otherLiteral : "1");
        }
        return Optional.of(destination.toString());
    }

    /**
     * Fills the variables in their order of appearance.
     *
     * @param values one value per variable; each value is converted with {@link String#valueOf(Object)}
     * @return the concrete destination
     * @throws IllegalArgumentException if the number of values does not match, or a value is empty or contains a {@code /}
     */
    String expand(Object... values) {
        if (values.length != variableNames.size()) {
            throw new IllegalArgumentException("The websocket topic " + template + " expects " + variableNames.size() + " values, but got " + values.length);
        }
        StringBuilder destination = new StringBuilder();
        int valueIndex = 0;
        for (int i = 0; i < literals.size(); i++) {
            destination.append('/');
            String literal = literals.get(i);
            if (literal != null) {
                destination.append(literal);
            }
            else {
                String value = String.valueOf(values[valueIndex++]);
                if (value.isEmpty() || value.indexOf('/') >= 0) {
                    throw new IllegalArgumentException("The value '" + value + "' for {" + variables.get(i) + "} in the websocket topic " + template + " is empty or contains a /");
                }
                destination.append(value);
            }
        }
        return destination.toString();
    }

    /**
     * Matches a concrete destination against this template.
     *
     * @param destination the destination, e.g. {@code /topic/exercise/42/newResults}
     * @return the variable values by name if the destination matches, empty otherwise
     */
    Optional<Map<String, String>> match(String destination) {
        if (destination == null || !destination.startsWith("/")) {
            return Optional.empty();
        }
        String[] segments = destination.substring(1).split("/", -1);
        if (segments.length != literals.size()) {
            return Optional.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            String literal = literals.get(i);
            if (literal != null) {
                if (!literal.equals(segment)) {
                    return Optional.empty();
                }
            }
            else if (segment.isEmpty()) {
                return Optional.empty();
            }
            else {
                values.put(variables.get(i), segment);
            }
        }
        return Optional.of(Collections.unmodifiableMap(values));
    }

    @Override
    public String toString() {
        return template;
    }
}
