package de.tum.cit.aet.artemis.core.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;

public class TooManyRequestsAlertException extends HttpStatusException {

    public TooManyRequestsAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public TooManyRequestsAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, HttpStatus.TOO_MANY_REQUESTS, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
