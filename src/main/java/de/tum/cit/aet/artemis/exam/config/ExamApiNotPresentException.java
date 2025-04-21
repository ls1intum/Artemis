package de.tum.cit.aet.artemis.exam.config;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_ENABLED_PROPERTY_NAME;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;

/**
 * Exception to be thrown when the exam API is not present.
 */
public class ExamApiNotPresentException extends ApiConditionNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public ExamApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, EXAM_ENABLED_PROPERTY_NAME);
    }
}
