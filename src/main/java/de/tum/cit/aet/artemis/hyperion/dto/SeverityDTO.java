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

    @JsonCreator
    public static SeverityDTO fromValue(String value) {
        if (value == null) {
            return null;
        }
        return KNOWN_VALUES.getOrDefault(value.toUpperCase(), new SeverityDTO(value.toUpperCase()));
    }

    public static SeverityDTO valueOf(String name) {
        var severity = KNOWN_VALUES.get(name.toUpperCase());
        if (severity == null) {
            throw new IllegalArgumentException("Unknown SeverityDTO value: " + name);
        }
        return severity;
    }

    @Override
    public String toString() {
        return value;
    }
}
