package de.tum.cit.aet.artemis.core.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum for artifact types in consistency checks.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum ArtifactType {

    PROBLEM_STATEMENT("PROBLEM_STATEMENT"), TEMPLATE_REPOSITORY("TEMPLATE_REPOSITORY"), SOLUTION_REPOSITORY("SOLUTION_REPOSITORY"), TESTS_REPOSITORY("TESTS_REPOSITORY");

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
