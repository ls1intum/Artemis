package de.tum.cit.aet.artemis.core.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;

public class NotImplementedAlertException extends HttpStatusException {

    public NotImplementedAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public NotImplementedAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, HttpStatus.NOT_IMPLEMENTED, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
