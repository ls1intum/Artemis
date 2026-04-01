package de.tum.cit.aet.artemis.core.exception;

import java.net.URI;

import org.zalando.problem.Status;

public class ServiceUnavailableAlertException extends HttpStatusException {

    public ServiceUnavailableAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public ServiceUnavailableAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, Status.SERVICE_UNAVAILABLE, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }
}
