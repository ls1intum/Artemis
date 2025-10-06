package de.tum.cit.aet.artemis.nebula.exception;

import static de.tum.cit.aet.artemis.core.config.Constants.NEBULA_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception that the optional Nebula API is not present.
 * This is caused by excluding artemis.nebula.enabled or setting it to false
 */
public class NebulaNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public NebulaNotPresentException(Class<? extends AbstractApi> api) {
        super(api, NEBULA_ENABLED_PROPERTY_NAME);
    }
}
