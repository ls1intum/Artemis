package de.tum.cit.aet.artemis.athena.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ATHENA_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception thrown when an Athena API is not present because the Athena module is disabled.
 * This occurs when artemis.athena.enabled is set to false in the configuration.
 */
public class AthenaApiNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the Athena API class that should be present
     */
    public AthenaApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, ATHENA_ENABLED_PROPERTY_NAME);
    }
}
