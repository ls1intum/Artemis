package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_MAX_QUESTION_INIT;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_MAX_QUESTION_LIMIT;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_MIN_QUESTION_INIT;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_TIME_LIMIT_IN_CLASS_MINUTES_INIT;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_TIME_LIMIT_IN_CLASS_MINUTES_MAX;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_TIME_LIMIT_QUESTION_SECONDS_INIT;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_TIME_LIMIT_QUESTION_SECONDS_MAX;

import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Prompting-mode quiz configuration stored in the Iris course settings JSON payload.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisPromptingModeSettings(@Min(1) @Max(IRIS_PROMPTING_MODE_MAX_QUESTION_LIMIT) int minQuestions,
        @Min(1) @Max(IRIS_PROMPTING_MODE_MAX_QUESTION_LIMIT) int maxQuestions, @Min(1) @Max(IRIS_PROMPTING_MODE_TIME_LIMIT_QUESTION_SECONDS_MAX) int timeLimitQuestion,
        @Min(1) @Max(IRIS_PROMPTING_MODE_TIME_LIMIT_IN_CLASS_MINUTES_MAX) int timeLimitInClass) implements Serializable {

    private static final IrisPromptingModeSettings DEFAULT = new IrisPromptingModeSettings(IRIS_PROMPTING_MODE_MIN_QUESTION_INIT, IRIS_PROMPTING_MODE_MAX_QUESTION_INIT,
            IRIS_PROMPTING_MODE_TIME_LIMIT_QUESTION_SECONDS_INIT, IRIS_PROMPTING_MODE_TIME_LIMIT_IN_CLASS_MINUTES_INIT);

    @JsonCreator
    public IrisPromptingModeSettings(@JsonProperty("minQuestions") @Nullable Integer minQuestions, @JsonProperty("maxQuestions") @Nullable Integer maxQuestions,
            @JsonProperty("timeLimitQuestion") @Nullable Integer timeLimitQuestion, @JsonProperty("timeLimitInClass") @Nullable Integer timeLimitInClass) {
        this(minQuestions != null ? minQuestions.intValue() : IRIS_PROMPTING_MODE_MIN_QUESTION_INIT,
                maxQuestions != null ? maxQuestions.intValue() : IRIS_PROMPTING_MODE_MAX_QUESTION_INIT,
                timeLimitQuestion != null ? timeLimitQuestion.intValue() : IRIS_PROMPTING_MODE_TIME_LIMIT_QUESTION_SECONDS_INIT,
                timeLimitInClass != null ? timeLimitInClass.intValue() : IRIS_PROMPTING_MODE_TIME_LIMIT_IN_CLASS_MINUTES_INIT);
    }

    public static IrisPromptingModeSettings defaultSettings() {
        return DEFAULT;
    }
}
