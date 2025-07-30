package de.tum.cit.aet.artemis.nebula.config;

import static de.tum.cit.aet.artemis.core.config.Constants.NEBULA_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception that an optionally autowired API is not present.
 * This is caused by a Spring property not being present.
 */
public class NebulaNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public NebulaNotPresentException(Class<? extends AbstractApi> api) {
        super(api, NEBULA_ENABLED_PROPERTY_NAME);
    }
}
