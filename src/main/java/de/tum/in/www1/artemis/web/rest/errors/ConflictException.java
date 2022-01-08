package de.tum.in.www1.artemis.web.rest.errors;

import java.net.URI;

public class ConflictException extends HttpStatusException {

    public ConflictException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public ConflictException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
