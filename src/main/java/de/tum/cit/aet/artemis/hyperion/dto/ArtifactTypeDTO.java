package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Immutable artifact type representation for consistency checks.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ArtifactTypeDTO(@JsonValue String value) {

    public static final ArtifactTypeDTO PROBLEM_STATEMENT = new ArtifactTypeDTO("PROBLEM_STATEMENT");

    public static final ArtifactTypeDTO TEMPLATE_REPOSITORY = new ArtifactTypeDTO("TEMPLATE_REPOSITORY");

    public static final ArtifactTypeDTO SOLUTION_REPOSITORY = new ArtifactTypeDTO("SOLUTION_REPOSITORY");

    public static final ArtifactTypeDTO TESTS_REPOSITORY = new ArtifactTypeDTO("TESTS_REPOSITORY");

    private static final Map<String, ArtifactTypeDTO> KNOWN_TYPES = Map.of( //
            PROBLEM_STATEMENT.value(), PROBLEM_STATEMENT, //
            TEMPLATE_REPOSITORY.value(), TEMPLATE_REPOSITORY, //
            SOLUTION_REPOSITORY.value(), SOLUTION_REPOSITORY, //
            TESTS_REPOSITORY.value(), TESTS_REPOSITORY);

    public ArtifactTypeDTO {
        Objects.requireNonNull(value, "value must not be null");
    }

    @JsonCreator
    public static ArtifactTypeDTO fromValue(String value) {
        if (value == null) {
            return null;
        }
        return KNOWN_TYPES.getOrDefault(value, new ArtifactTypeDTO(value));
    }

    public static ArtifactTypeDTO valueOf(String name) {
        var type = KNOWN_TYPES.get(name);
        if (type == null) {
            throw new IllegalArgumentException("Unknown ArtifactTypeDTO value: " + name);
        }
        return type;
    }

    @Override
    public String toString() {
        return value;
    }
}
