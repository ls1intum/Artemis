package de.tum.cit.aet.artemis.versioning.config;

import static de.tum.cit.aet.artemis.core.config.Constants.EXERCISE_VERSIONING_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception that an optionally autowired API is not present.
 * This is caused by a Spring property not being present.
 */
public class ExerciseVersioningNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public ExerciseVersioningNotPresentException(Class<? extends AbstractApi> api) {
        super(api, EXERCISE_VERSIONING_ENABLED_PROPERTY_NAME);
    }
}
