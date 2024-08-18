package de.tum.in.www1.artemis.web.rest.errors;

import java.net.URI;

import org.zalando.problem.Status;

public class NotFoundAlertException extends HttpStatusException {

    public NotFoundAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.ENTITY_NOT_FOUND_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public NotFoundAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, Status.NOT_FOUND, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }

}
