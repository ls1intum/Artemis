package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum for consistency issue severity levels.
 */
public enum Severity {

    LOW("LOW"), MEDIUM("MEDIUM"), HIGH("HIGH");

    private final String value;

    Severity(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
