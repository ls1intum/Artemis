package de.tum.cit.aet.artemis.core.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;

public class InternalServerErrorAlertException extends HttpStatusException {

    public InternalServerErrorAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public InternalServerErrorAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, HttpStatus.INTERNAL_SERVER_ERROR, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
