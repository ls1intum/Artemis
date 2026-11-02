package de.tum.cit.aet.artemis.core.security.allowedScopes;

import java.util.Arrays;
import java.util.Optional;

/**
 * A capability a personal access token can be limited to.
 * <p>
 * A scope narrows what a token may do; it never widens it. A request authenticated with a token is authorized as the
 * token's owner (their role in the course) and, on top of that, only reaches endpoints that declare the scope with
 * {@link AllowedScopes}. There is deliberately no scope for grades, exams, student data, deletion or publishing.
 */
public enum AgentScope {

    COURSES_READ("courses:read"), COMPETENCIES_READ("competencies:read"), COMPETENCIES_WRITE("competencies:write"), ANNOUNCEMENTS_POST("announcements:post"),
    EXERCISES_DRAFT("exercises:draft"), PROGRAMMING_DRAFT("programming:draft"), LECTURES_WRITE("lectures:write");

    private final String value;

    AgentScope(String value) {
        this.value = value;
    }

    /**
     * The name of the scope as it appears in the REST API and in stored tokens.
     *
     * @return the scope name, for example {@code competencies:write}
     */
    public String getValue() {
        return value;
    }

    /**
     * Resolves a scope from its API name.
     *
     * @param value the scope name
     * @return the scope, or empty if the name is not a known scope
     */
    public static Optional<AgentScope> fromValue(String value) {
        return Arrays.stream(values()).filter(scope -> scope.value.equals(value)).findFirst();
    }
}
