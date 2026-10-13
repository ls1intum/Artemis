package de.tum.cit.aet.artemis.hyperion.domain;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON object persisted for the Hyperion settings of one course.
 *
 * @param poolGenerationEnabled   whether the quiz question pool is generated automatically for new lecture material
 * @param candidatesPerCompetency questions the pool holds per competency, spread over that competency's cells
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record HyperionCourseSettings(boolean poolGenerationEnabled, int candidatesPerCompetency) implements Serializable {

    public static final int DEFAULT_CANDIDATES_PER_COMPETENCY = 20;

    public static final int MIN_CANDIDATES_PER_COMPETENCY = 1;

    public static final int MAX_CANDIDATES_PER_COMPETENCY = 200;

    private static final HyperionCourseSettings DEFAULT = new HyperionCourseSettings(false, DEFAULT_CANDIDATES_PER_COMPETENCY);

    /**
     * Creates settings from a persisted payload, substituting the defaults for fields the payload does not carry.
     *
     * @param poolGenerationEnabled   the stored flag, or {@code null} when the payload predates it
     * @param candidatesPerCompetency the stored count, or {@code null} when the payload predates it
     */
    @JsonCreator
    public HyperionCourseSettings(@JsonProperty("poolGenerationEnabled") @Nullable Boolean poolGenerationEnabled,
            @JsonProperty("candidatesPerCompetency") @Nullable Integer candidatesPerCompetency) {
        this(Boolean.TRUE.equals(poolGenerationEnabled), candidatesPerCompetency == null ? DEFAULT_CANDIDATES_PER_COMPETENCY : candidatesPerCompetency);
    }

    public static HyperionCourseSettings defaultSettings() {
        return DEFAULT;
    }
}
