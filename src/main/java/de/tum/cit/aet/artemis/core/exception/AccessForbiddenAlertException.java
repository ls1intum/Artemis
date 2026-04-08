package de.tum.cit.aet.artemis.core.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;

public class AccessForbiddenAlertException extends HttpStatusException {

    public AccessForbiddenAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public AccessForbiddenAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, HttpStatus.FORBIDDEN, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
