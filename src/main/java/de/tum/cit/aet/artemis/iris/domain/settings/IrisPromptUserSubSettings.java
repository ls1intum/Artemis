package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_MAX_QUESTION_INIT;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_MIN_QUESTION_INIT;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_TIME_LIMIT_IN_CLASS_MINUTES_INIT;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_TIME_LIMIT_QUESTION_SECONDS_INIT;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSubSettings} implementation for the settings for the prompt user mode of iris.
 */
@Entity
@DiscriminatorValue("PROMPT_USER")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisPromptUserSubSettings extends IrisSubSettings {

    @Min(1)
    @Column(name = "min_questions")
    private int minQuestions = IRIS_PROMPTING_MODE_MIN_QUESTION_INIT;

    @Min(1)
    @Column(name = "max_questions")
    private int maxQuestions = IRIS_PROMPTING_MODE_MAX_QUESTION_INIT;

    @Min(1)
    @Column(name = "time_limit_question")
    private int timeLimitQuestion = IRIS_PROMPTING_MODE_TIME_LIMIT_QUESTION_SECONDS_INIT;

    @Min(1)
    @Column(name = "time_limit_in_class")
    private int timeLimitInClass = IRIS_PROMPTING_MODE_TIME_LIMIT_IN_CLASS_MINUTES_INIT;

    public int getMinQuestions() {
        return minQuestions;
    }

    public void setMinQuestions(int minQuestions) {
        this.minQuestions = minQuestions;
    }

    public int getMaxQuestions() {
        return maxQuestions;
    }

    public void setMaxQuestions(int maxQuestions) {
        this.maxQuestions = maxQuestions;
    }

    public int getTimeLimitQuestion() {
        return timeLimitQuestion;
    }

    public void setTimeLimitQuestion(int timeLimitQuestion) {
        this.timeLimitQuestion = timeLimitQuestion;
    }

    public int getTimeLimitInClass() {
        return timeLimitInClass;
    }

    public void setTimeLimitInClass(int timeLimitInClass) {
        this.timeLimitInClass = timeLimitInClass;
    }
}
