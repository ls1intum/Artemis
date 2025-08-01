package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum for artifact types in consistency checks.
 */
public enum ArtifactType {

    PROBLEM_STATEMENT("PROBLEM_STATEMENT"), TEMPLATE_REPOSITORY("TEMPLATE_REPOSITORY"), SOLUTION_REPOSITORY("SOLUTION_REPOSITORY");

    private final String value;

    ArtifactType(String value) {
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
