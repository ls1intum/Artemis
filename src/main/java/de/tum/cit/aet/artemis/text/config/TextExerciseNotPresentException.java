package de.tum.cit.aet.artemis.text.config;

import static de.tum.cit.aet.artemis.core.config.Constants.TEXT_EXERCISE_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception that an optionally autowired API is not present.
 * This is caused by a Spring property not being present.
 */
public class TextExerciseNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public TextExerciseNotPresentException(Class<? extends AbstractApi> api) {
        super(api, TEXT_EXERCISE_ENABLED_PROPERTY_NAME);
    }
}
