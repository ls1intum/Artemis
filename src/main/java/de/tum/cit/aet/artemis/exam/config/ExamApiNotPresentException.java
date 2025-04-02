package de.tum.cit.aet.artemis.exam.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.exception.ApiProfileNotPresentException;

public class ExamApiNotPresentException extends ApiProfileNotPresentException {

    public ExamApiNotPresentException(Class<? extends AbstractApi> api) {
        super(api, PROFILE_CORE);
    }
}
