package de.tum.cit.aet.artemis.math.config;

import static de.tum.cit.aet.artemis.core.config.Constants.MATH_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception that an optionally autowired math API is not present.
 */
public class MathApiNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public MathApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, MATH_ENABLED_PROPERTY_NAME);
    }
}
