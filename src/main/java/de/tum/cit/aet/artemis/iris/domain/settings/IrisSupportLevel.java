package de.tum.cit.aet.artemis.iris.domain.settings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Instructional support level for Iris at the course level.
 * Controls how much guidance the AI provides to students:
 * - LOW encourages independent problem-solving via Socratic questioning
 * - MODERATE provides balanced guidance (default)
 * - HIGH offers detailed step-by-step help
 */
public enum IrisSupportLevel {

    LOW("low"), MODERATE("moderate"), HIGH("high");

    private final String jsonValue;

    IrisSupportLevel(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    /**
     * Creates an IrisSupportLevel from its JSON string representation.
     * <p>
     * Both an absent/null value and an unrecognized value deliberately fall back to {@link #MODERATE} rather than failing. This is intentional and mirrors
     * {@link IrisPipelineVariant#fromJson}: course settings are persisted as a single JSON column with no schema migration, so older rows simply omit the
     * field, and we never want a stale or malformed client payload to make the course's Iris settings unloadable. The {@link IrisCourseSettings} canonical
     * constructor applies the same MODERATE fallback for the case where the {@code supportLevel} key is entirely absent from the JSON (in which case this
     * creator is not invoked at all); the two layers cover the absent-key and explicit-value paths respectively.
     *
     * @param raw the raw JSON string value
     * @return the corresponding support level, defaulting to MODERATE if null or not recognized
     */
    @JsonCreator
    public static IrisSupportLevel fromJson(String raw) {
        if (raw == null) {
            return MODERATE;
        }
        return switch (raw.toLowerCase()) {
            case "low" -> LOW;
            case "moderate" -> MODERATE;
            case "high" -> HIGH;
            default -> MODERATE;
        };
    }
}
