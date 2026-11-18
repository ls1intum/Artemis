package de.tum.cit.aet.artemis.core.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;

public class ConflictException extends HttpStatusException {

    public ConflictException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public ConflictException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, HttpStatus.CONFLICT, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
