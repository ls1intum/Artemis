package de.tum.cit.aet.artemis.modeling.config;

import static de.tum.cit.aet.artemis.core.config.Constants.MODELING_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception that an optionally autowired API is not present.
 */
public class ModelingApiNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public ModelingApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, MODELING_ENABLED_PROPERTY_NAME);
    }
}
