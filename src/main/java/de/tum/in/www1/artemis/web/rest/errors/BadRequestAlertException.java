package de.tum.in.www1.artemis.web.rest.errors;

import java.net.URI;
import java.util.Map;

public class BadRequestAlertException extends HttpStatusException {

    public BadRequestAlertException(String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, skipAlert);
    }

    public BadRequestAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public BadRequestAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }

    public BadRequestAlertException(URI type, String defaultMessage, String entityName, String errorKey, Map<String, Object> parameters) {
        super(type, defaultMessage, entityName, errorKey, parameters);
    }
}
