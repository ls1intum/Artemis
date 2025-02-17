package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A value specifying the severity level of a result.
 */
public enum Level {

    NONE("none"), NOTE("note"), WARNING("warning"), ERROR("error");

    private final String value;

    private static final Map<String, Level> CONSTANTS = new HashMap<>();

    static {
        for (Level c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    Level(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    /**
     * Creates a {@link Level} instance from a given string value.
     *
     * @param value the string representation of the {@link Level}
     * @return the matching {@link Level} instance
     * @throws IllegalArgumentException if the provided value does not correspond to any defined {@link Level}
     */
    @JsonCreator
    public static Level fromValue(String value) {
        Level constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        }
        else {
            return constant;
        }
    }

}
