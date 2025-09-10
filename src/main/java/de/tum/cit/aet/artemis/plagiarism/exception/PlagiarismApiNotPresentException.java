package de.tum.cit.aet.artemis.plagiarism.exception;

import static de.tum.cit.aet.artemis.core.config.Constants.PLAGIARISM_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception thrown when the Plagiarism API is not present.
 */
public class PlagiarismApiNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public PlagiarismApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, PLAGIARISM_ENABLED_PROPERTY_NAME);
    }
}
