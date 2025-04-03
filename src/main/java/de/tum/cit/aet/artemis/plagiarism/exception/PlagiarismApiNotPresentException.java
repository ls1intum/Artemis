package de.tum.cit.aet.artemis.plagiarism.exception;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiProfileNotPresentException;

/**
 * Exception thrown when the Plagiarism API is not present.
 */
public class PlagiarismApiNotPresentException extends ApiProfileNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public PlagiarismApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, PROFILE_CORE);
    }
}
