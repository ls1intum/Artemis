package de.tum.in.www1.artemis.web.rest.errors;

import java.net.URI;
import java.util.Map;

import org.zalando.problem.Status;

public class BadRequestAlertException extends HttpStatusException {

    public BadRequestAlertException(String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, skipAlert);
    }

    public BadRequestAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public BadRequestAlertException(String defaultMessage, String entityName, String errorKey, Map<String, Object> translationParameters) {
        // translation params are expected to be a child of the "params" object
        this(ErrorConstants.PARAMETERIZED_TYPE, defaultMessage, entityName, errorKey, Map.of("params", translationParameters));
    }

    public BadRequestAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, Status.BAD_REQUEST, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }

    public BadRequestAlertException(URI type, String defaultMessage, String entityName, String errorKey, Map<String, Object> parameters) {
        super(type, defaultMessage, Status.BAD_REQUEST, entityName, errorKey, parameters);
    }
}
