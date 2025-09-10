package de.tum.cit.aet.artemis.lecture.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiProfileNotPresentException;

/**
 * Exception thrown when the Lecture API is not present.
 */
public class LectureApiNotPresentException extends ApiProfileNotPresentException {

    /**
     * @param api the api class that should be present
     */
    public LectureApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, PROFILE_CORE);
    }
}
