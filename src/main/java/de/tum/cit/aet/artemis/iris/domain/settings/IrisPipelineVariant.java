package de.tum.cit.aet.artemis.iris.domain.settings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported pipeline variants for Iris at the course level.
 */
public enum IrisPipelineVariant {

    DEFAULT("default"), ADVANCED("advanced");

    private final String jsonValue;

    IrisPipelineVariant(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static IrisPipelineVariant fromJson(String raw) {
        if (raw == null) {
            return DEFAULT;
        }
        return switch (raw.toLowerCase()) {
            case "advanced" -> ADVANCED;
            case "default" -> DEFAULT;
            default -> DEFAULT;
        };
    }
}
