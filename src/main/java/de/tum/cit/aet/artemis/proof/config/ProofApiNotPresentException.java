package de.tum.cit.aet.artemis.proof.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROOF_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception that an optionally autowired proof API is not present.
 */
public class ProofApiNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public ProofApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, PROOF_ENABLED_PROPERTY_NAME);
    }
}
