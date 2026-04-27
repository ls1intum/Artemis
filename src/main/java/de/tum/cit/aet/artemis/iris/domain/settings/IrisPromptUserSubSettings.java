package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_MAX_QUESTION_INIT;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_PROMPTING_MODE_MIN_QUESTION_INIT;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSubSettings} implementation for the settings for the prompt user mode of iris.
 */
@Entity
@DiscriminatorValue("PROMPT_USER")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisPromptUserSubSettings extends IrisSubSettings {

    @Column(name = "min_questions")
    private int minQuestions = IRIS_PROMPTING_MODE_MIN_QUESTION_INIT;

    @Column(name = "max_questions")
    private int maxQuestions = IRIS_PROMPTING_MODE_MAX_QUESTION_INIT;

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
}
