package de.tum.cit.aet.artemis.core.exception;

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
