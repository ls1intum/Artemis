package de.tum.cit.aet.artemis.web.rest.errors;

import java.net.URI;

import org.zalando.problem.Status;

public class InternalServerErrorAlertException extends HttpStatusException {

    public InternalServerErrorAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public InternalServerErrorAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, Status.INTERNAL_SERVER_ERROR, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
