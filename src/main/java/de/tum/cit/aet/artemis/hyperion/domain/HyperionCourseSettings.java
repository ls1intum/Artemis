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
 * @param questionsPerLectureUnit questions the pool holds per lecture unit and competency, spread over that unit's cells
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record HyperionCourseSettings(boolean poolGenerationEnabled, int questionsPerLectureUnit) implements Serializable {

    public static final int DEFAULT_QUESTIONS_PER_LECTURE_UNIT = 12;

    public static final int MIN_QUESTIONS_PER_LECTURE_UNIT = 1;

    public static final int MAX_QUESTIONS_PER_LECTURE_UNIT = 60;

    private static final HyperionCourseSettings DEFAULT = new HyperionCourseSettings(false, DEFAULT_QUESTIONS_PER_LECTURE_UNIT);

    /**
     * Creates settings from a persisted payload, substituting the defaults for fields the payload does not carry.
     *
     * @param poolGenerationEnabled   the stored flag, or {@code null} when the payload predates it
     * @param questionsPerLectureUnit the stored count, or {@code null} when the payload predates it
     */
    @JsonCreator
    public HyperionCourseSettings(@JsonProperty("poolGenerationEnabled") @Nullable Boolean poolGenerationEnabled,
            @JsonProperty("questionsPerLectureUnit") @Nullable Integer questionsPerLectureUnit) {
        this(Boolean.TRUE.equals(poolGenerationEnabled), questionsPerLectureUnit == null ? DEFAULT_QUESTIONS_PER_LECTURE_UNIT : questionsPerLectureUnit);
    }

    public static HyperionCourseSettings defaultSettings() {
        return DEFAULT;
    }
}
