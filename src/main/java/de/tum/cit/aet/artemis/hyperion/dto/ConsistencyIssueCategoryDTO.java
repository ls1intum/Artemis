package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Unified category representation for consistency issues. Composed of structural and semantic categories.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConsistencyIssueCategoryDTO(@JsonValue String value) {

    // Structural
    public static final ConsistencyIssueCategoryDTO METHOD_RETURN_TYPE_MISMATCH = new ConsistencyIssueCategoryDTO("METHOD_RETURN_TYPE_MISMATCH");

    public static final ConsistencyIssueCategoryDTO METHOD_PARAMETER_MISMATCH = new ConsistencyIssueCategoryDTO("METHOD_PARAMETER_MISMATCH");

    public static final ConsistencyIssueCategoryDTO CONSTRUCTOR_PARAMETER_MISMATCH = new ConsistencyIssueCategoryDTO("CONSTRUCTOR_PARAMETER_MISMATCH");

    public static final ConsistencyIssueCategoryDTO ATTRIBUTE_TYPE_MISMATCH = new ConsistencyIssueCategoryDTO("ATTRIBUTE_TYPE_MISMATCH");

    public static final ConsistencyIssueCategoryDTO VISIBILITY_MISMATCH = new ConsistencyIssueCategoryDTO("VISIBILITY_MISMATCH");

    // Semantic
    public static final ConsistencyIssueCategoryDTO IDENTIFIER_NAMING_INCONSISTENCY = new ConsistencyIssueCategoryDTO("IDENTIFIER_NAMING_INCONSISTENCY");

    private static final Map<String, ConsistencyIssueCategoryDTO> KNOWN_CATEGORIES = Map.ofEntries( //
            Map.entry(METHOD_RETURN_TYPE_MISMATCH.value(), METHOD_RETURN_TYPE_MISMATCH), //
            Map.entry(METHOD_PARAMETER_MISMATCH.value(), METHOD_PARAMETER_MISMATCH), //
            Map.entry(CONSTRUCTOR_PARAMETER_MISMATCH.value(), CONSTRUCTOR_PARAMETER_MISMATCH), //
            Map.entry(ATTRIBUTE_TYPE_MISMATCH.value(), ATTRIBUTE_TYPE_MISMATCH), //
            Map.entry(VISIBILITY_MISMATCH.value(), VISIBILITY_MISMATCH), //
            Map.entry(IDENTIFIER_NAMING_INCONSISTENCY.value(), IDENTIFIER_NAMING_INCONSISTENCY));

    public ConsistencyIssueCategoryDTO {
        Objects.requireNonNull(value, "value must not be null");
    }

    @JsonCreator
    public static ConsistencyIssueCategoryDTO fromValue(String value) {
        if (value == null) {
            return null;
        }
        var category = KNOWN_CATEGORIES.get(value);
        return category != null ? category : new ConsistencyIssueCategoryDTO(value);
    }

    public static ConsistencyIssueCategoryDTO valueOf(String name) {
        var category = KNOWN_CATEGORIES.get(name);
        if (category == null) {
            throw new IllegalArgumentException("Unknown ConsistencyIssueCategoryDTO value: " + name);
        }
        return category;
    }

    @Override
    public String toString() {
        return value;
    }
}
