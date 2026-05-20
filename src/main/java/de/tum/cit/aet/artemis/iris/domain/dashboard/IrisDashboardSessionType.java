package de.tum.cit.aet.artemis.iris.domain.dashboard;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum IrisDashboardSessionType {

    PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT, COURSE_CHAT, LECTURE_CHAT, TUTOR_SUGGESTION;

    /**
     * @return true for the synthetic tutor suggestion dashboard type
     */
    public boolean isTutorSuggestion() {
        return this == TUTOR_SUGGESTION;
    }

    /**
     * @return the value stored in Iris chat session rows
     */
    public String databaseValue() {
        return name();
    }

    /**
     * Parses the optional dashboard chat mode request parameter.
     *
     * @param value the request parameter value
     * @return the matching dashboard session type
     */
    public static IrisDashboardSessionType fromRequestParameter(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported Iris dashboard chatMode '" + value + "'. Supported values are " + Arrays.toString(IrisDashboardSessionType.values()));
        }
        try {
            return IrisDashboardSessionType.valueOf(value.trim());
        }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported Iris dashboard chatMode '" + value + "'. Supported values are " + Arrays.toString(IrisDashboardSessionType.values()));
        }
    }
}
