package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Consistency issue severity levels.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SeverityDTO(@JsonValue String value) {

    public static final SeverityDTO LOW = new SeverityDTO("LOW");

    public static final SeverityDTO MEDIUM = new SeverityDTO("MEDIUM");

    public static final SeverityDTO HIGH = new SeverityDTO("HIGH");

    private static final Map<String, SeverityDTO> KNOWN_VALUES = Map.of( //
            LOW.value(), LOW, //
            MEDIUM.value(), MEDIUM, //
            HIGH.value(), HIGH);

    public SeverityDTO {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Resolve a severity from serialized value, falling back to upper-cased custom values.
     *
     * @param value stored value or {@code null}
     * @return matching {@link SeverityDTO} or {@code null}
     */
    @JsonCreator
    public static SeverityDTO fromValue(String value) {
        if (value == null) {
            return null;
        }
        return resolve(value.toUpperCase());
    }

    /**
     * Retains the former enum API entry point for switch statements.
     *
     * @param name canonical constant name
     * @return matching {@link SeverityDTO}
     */
    public static SeverityDTO valueOf(String name) {
        return resolve(name.toUpperCase());
    }

    private static SeverityDTO resolve(String normalizedName) {
        var severity = KNOWN_VALUES.get(normalizedName);
        if (severity == null) {
            throw new IllegalArgumentException("Unknown SeverityDTO value: " + normalizedName);
        }
        return severity;
    }

    @Override
    public String toString() {
        return value;
    }
}
