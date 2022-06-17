package de.tum.in.www1.artemis.web.rest.errors;

import java.net.URI;

import org.zalando.problem.Status;

public class ConflictException extends HttpStatusException {

    public ConflictException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public ConflictException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, Status.CONFLICT, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
