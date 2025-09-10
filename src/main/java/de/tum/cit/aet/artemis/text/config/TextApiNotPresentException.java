package de.tum.cit.aet.artemis.text.config;

import static de.tum.cit.aet.artemis.core.config.Constants.TEXT_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception that an optionally autowired API is not present.
 */
public class TextApiNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public TextApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, TEXT_ENABLED_PROPERTY_NAME);
    }
}
