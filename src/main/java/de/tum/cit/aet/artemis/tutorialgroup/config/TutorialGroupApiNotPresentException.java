package de.tum.cit.aet.artemis.tutorialgroup.config;

import static de.tum.cit.aet.artemis.core.config.Constants.TUTORIAL_GROUP_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;
import de.tum.cit.aet.artemis.tutorialgroup.api.AbstractTutorialGroupApi;

/**
 * Exception that an optionally autowired API is not present.
 * This is caused by a Spring property not being present.
 */
public class TutorialGroupApiNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public TutorialGroupApiNotPresentException(Class<? extends AbstractTutorialGroupApi> api) {
        super(api, TUTORIAL_GROUP_ENABLED_PROPERTY_NAME);
    }
}
