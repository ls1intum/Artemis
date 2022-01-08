package de.tum.in.www1.artemis.web.rest.errors;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends HttpStatusException {

    public ConflictException(String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, skipAlert);
    }

    public ConflictException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public ConflictException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
